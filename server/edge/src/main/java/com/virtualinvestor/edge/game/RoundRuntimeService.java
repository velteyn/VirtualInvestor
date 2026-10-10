package com.virtualinvestor.edge.game;

import com.virtualinvestor.core.clock.RealTimeDriver;
import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.RoundState;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.participant.HumanParticipant;
import com.virtualinvestor.edge.wire.WireMessage;
import com.virtualinvestor.runtime.RunningRound;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Spring-managed owner of running rounds (design D1/D2). It creates a {@link RunningRound},
 * starts its real-time driver, and exposes lifecycle control. The edge marshals client commands
 * onto the round's command queue; the loop stays single-threaded.
 */
@Service
public class RoundRuntimeService {

    private final RoundBroadcaster broadcaster;
    private final NewsroomService newsroom;
    private final Map<String, RunningRound> rounds = new ConcurrentHashMap<>();
    private final Map<String, RealTimeDriver> drivers = new ConcurrentHashMap<>();
    /** Held rooms remember whether to drive in real time once started. */
    private final Map<String, Boolean> realTime = new ConcurrentHashMap<>();

    public RoundRuntimeService(RoundBroadcaster broadcaster) {
        this(broadcaster, null);
    }

    /**
     * Production wiring: the newsroom files articles per round (newsroom-narrator
     * 2.3). Tests keep the single-arg constructor, which runs without narration.
     */
    @org.springframework.beans.factory.annotation.Autowired
    public RoundRuntimeService(RoundBroadcaster broadcaster, NewsroomService newsroom) {
        this.broadcaster = broadcaster;
        this.newsroom = newsroom;
    }

    /** Create and start a round, running its loop in real time. */
    public RunningRound start(String roundId, long seed, LaunchConfig config, boolean runInRealTime) {
        return start(roundId, seed, config, runInRealTime, RunningRound.DEFAULT_STEP_MILLIS);
    }

    /** As above, but with an explicit step size (tests use a large step for a short session). */
    public RunningRound start(String roundId, long seed, LaunchConfig config, boolean runInRealTime, long stepMillis) {
        return start(roundId, seed, config, runInRealTime, stepMillis, List.of());
    }

    /** As above, with extra human seats for multiplayer rounds (multiplayer-seats 2.1). */
    public RunningRound start(String roundId, long seed, LaunchConfig config, boolean runInRealTime,
            long stepMillis, List<com.virtualinvestor.core.participant.HumanParticipant> extraHumans) {
        RunningRound round = new RunningRound(roundId, seed, config, stepMillis, extraHumans);
        round.setPublisher(new RoundEvents(roundId, broadcaster, newsroom));
        broadcaster.bindEngines(() -> round.engines().values().stream().toList());
        round.start();
        rounds.put(roundId, round);
        if (newsroom != null) {
            newsroom.preview(roundId, config.preset().name(), config.startingCapital());
        }
        if (runInRealTime) {
            // Drive the ROUND's step (not the bare clock), so the world actually advances.
            RealTimeDriver driver = new RealTimeDriver(round.clock(), round::stepQuietly);
            drivers.put(roundId, driver);
            driver.start();
        }
        return round;
    }

    public Optional<RunningRound> round(String roundId) {
        return Optional.ofNullable(rounds.get(roundId));
    }

    /**
     * Build a room's world and hold it (game-lobby 2.2): warm-up and roster exist,
     * the clock never starts, so joins rewire the roster freely until master start.
     */
    public RunningRound hold(String roundId, long seed, LaunchConfig config, long stepMillis,
            List<HumanParticipant> extras, boolean runInRealTime) {
        return hold(roundId, seed, config, stepMillis, extras, runInRealTime, "You");
    }

    /** As above with a display name for the first human seat (the room master). */
    public RunningRound hold(String roundId, long seed, LaunchConfig config, long stepMillis,
            List<HumanParticipant> extras, boolean runInRealTime, String playerName) {
        RunningRound round = new RunningRound(roundId, seed, config, stepMillis, extras, playerName);
        round.setPublisher(new RoundEvents(roundId, broadcaster, newsroom));
        broadcaster.bindEngines(() -> round.engines().values().stream().toList());
        round.hold();
        rounds.put(roundId, round);
        realTime.put(roundId, runInRealTime);
        if (newsroom != null) {
            newsroom.preview(roundId, config.preset().name(), config.startingCapital());
        }
        return round;
    }

    /**
     * Master-start a held room: boot the clock and the real-time driver.
     * Idempotent — starting a running room is a no-op success.
     */
    public void boot(String roundId) {
        RunningRound round = rounds.get(roundId);
        if (round == null) {
            throw new IllegalArgumentException("unknown round: " + roundId);
        }
        if (round.state() != RoundState.WAITING) {
            return;
        }
        round.start();
        if (Boolean.TRUE.equals(realTime.get(roundId))) {
            RealTimeDriver driver = new RealTimeDriver(round.clock(), round::stepQuietly);
            drivers.put(roundId, driver);
            driver.start();
        }
    }

    /** Drop a round entirely (room expiry): stops any driver and forgets the world. */
    public void forget(String roundId) {
        stop(roundId);
        rounds.remove(roundId);
        realTime.remove(roundId);
        if (newsroom != null) {
            newsroom.forget(roundId);
        }
    }

    /** Pause a running round: its clock stops advancing. */
    public void pause(String roundId) {
        rounds.get(roundId).pauseClock();
        rounds.get(roundId).lifecycle().transitionTo(RoundState.PAUSED);
    }

    public void resume(String roundId) {
        rounds.get(roundId).lifecycle().transitionTo(RoundState.RUNNING);
        rounds.get(roundId).resumeClock();
    }

    /** Enqueue a client order command onto a running round. */
    public void submit(String roundId, String participantId, WireMessage.Payload payload, long tick) {
        RunningRound round = rounds.get(roundId);
        if (round == null) {
            throw new IllegalArgumentException("unknown round: " + roundId);
        }
        Command command = com.virtualinvestor.edge.api.WireMapper.command(participantId, payload, tick);
        round.enqueue(command);
    }

    /**
     * A reconnect snapshot for one participant: their working orders (resting limits plus
     * untriggered stops), non-flat positions and account, as the same frames the live stream
     * emits — so the terminal can clear its mirror and repopulate it (task 5.2).
     */
    public java.util.List<WireMessage.Payload> snapshot(String roundId, String participantId) {
        RunningRound round = rounds.get(roundId);
        if (round == null) {
            throw new IllegalArgumentException("unknown round: " + roundId);
        }
        com.virtualinvestor.core.participant.Participant participant = round.participant(participantId);
        long tick = round.tick();
        java.util.List<WireMessage.Payload> frames = new java.util.ArrayList<>();
        for (com.virtualinvestor.core.engine.InstrumentEngine engine : round.engines().values()) {
            for (com.virtualinvestor.core.book.RestingOrder order : engine.book().orders()) {
                if (!order.participantId().equals(participantId)) {
                    continue;
                }
                long filled = order.filled();
                frames.add(new WireMessage.OrderDto(order.id(), participantId, engine.symbol(),
                        order.side().name(), com.virtualinvestor.core.domain.OrderType.LIMIT.name(),
                        order.quantity(), order.price(), 0.0,
                        filled > 0L ? com.virtualinvestor.core.domain.OrderStatus.PARTIAL.name()
                                : com.virtualinvestor.core.domain.OrderStatus.WORKING.name(),
                        filled, null, tick));
            }
            for (com.virtualinvestor.core.book.StopOrder stop : engine.stops().orders()) {
                if (!stop.participantId().equals(participantId)) {
                    continue;
                }
                frames.add(new WireMessage.OrderDto(stop.id(), participantId, engine.symbol(),
                        stop.side().name(), stop.type().name(), stop.quantity(), stop.limitPrice(),
                        stop.stopPrice(),
                        com.virtualinvestor.core.domain.OrderStatus.WORKING.name(), 0L, null, tick));
            }
        }
        com.virtualinvestor.core.participant.Account account = participant.account();
        for (com.virtualinvestor.core.participant.Position position : account.positions().values()) {
            if (position.isFlat()) {
                continue;
            }
            frames.add(new WireMessage.PositionDto(participantId, position.ticker(), position.netQty(),
                    position.avgPrice(), position.realized()));
        }
        frames.add(new WireMessage.AccountDto(
                participantId, account.cash(), account.realizedPnl(), account.feesPaid()));
        return frames;
    }

    public void stop(String roundId) {
        RealTimeDriver driver = drivers.remove(roundId);
        if (driver != null) {
            driver.close();
        }
    }
}
