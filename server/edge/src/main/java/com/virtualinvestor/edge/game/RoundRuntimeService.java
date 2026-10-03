package com.virtualinvestor.edge.game;

import com.virtualinvestor.core.clock.RealTimeDriver;
import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.RoundState;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.edge.wire.WireMessage;
import com.virtualinvestor.runtime.RunningRound;
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
    private final Map<String, RunningRound> rounds = new ConcurrentHashMap<>();
    private final Map<String, RealTimeDriver> drivers = new ConcurrentHashMap<>();

    public RoundRuntimeService(RoundBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    /** Create and start a round, running its loop in real time. */
    public RunningRound start(String roundId, long seed, LaunchConfig config, boolean runInRealTime) {
        return start(roundId, seed, config, runInRealTime, RunningRound.DEFAULT_STEP_MILLIS);
    }

    /** As above, but with an explicit step size (tests use a large step for a short session). */
    public RunningRound start(String roundId, long seed, LaunchConfig config, boolean runInRealTime, long stepMillis) {
        RunningRound round = new RunningRound(roundId, seed, config, stepMillis);
        round.setPublisher(broadcaster);
        broadcaster.bindEngines(() -> round.engines().values().stream().toList());
        round.start();
        rounds.put(roundId, round);
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

    public void stop(String roundId) {
        RealTimeDriver driver = drivers.remove(roundId);
        if (driver != null) {
            driver.close();
        }
    }
}
