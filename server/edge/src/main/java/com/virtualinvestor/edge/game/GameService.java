package com.virtualinvestor.edge.game;

import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.game.RivalRoster;
import com.virtualinvestor.core.game.RoundLifecycle;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.instrument.SymbolSearch;
import com.virtualinvestor.core.participant.BotParticipant;
import com.virtualinvestor.core.participant.HumanParticipant;
import com.virtualinvestor.core.participant.Participant;
import com.virtualinvestor.core.participant.Ranking;
import com.virtualinvestor.core.participant.Standing;
import com.virtualinvestor.core.rules.RealismPreset;
import com.virtualinvestor.persist.player.PlayerStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Spring-managed game service (design D2): owns round creation, the session-token registry and
 * queries built on the framework-free core. It holds no simulation logic of its own; the hot
 * world loop will be driven separately.
 */
@Service
public class GameService {

    private static final long STEP_MILLIS = 100L;

    private final InstrumentUniverse universe;
    private final SymbolSearch symbolSearch;
    private final RoundRuntimeService runtime;
    private final PlayerService players;
    private final javax.sql.DataSource dataSource;
    private final Map<String, String> sessionTokens = new ConcurrentHashMap<>();
    private final Map<String, RoundView> rounds = new ConcurrentHashMap<>();
    private final Map<String, Map<String, String>> roundPlayers = new ConcurrentHashMap<>();
    private final Map<String, RoundDurability> durability = new ConcurrentHashMap<>();
    private long roundCounter = 0L;

    /**
     * Boot-unique tag so round ids never collide with rows from previous server
     * runs sharing one database (stale rows once starved checkpoints).
     */
    private static final String BOOT_TAG = java.util.UUID.randomUUID().toString().substring(0, 4);

    public GameService(RoundRuntimeService runtime, PlayerService players, javax.sql.DataSource dataSource) {
        this.runtime = runtime;
        this.players = players;
        this.dataSource = dataSource;
        this.universe = InstrumentUniverse.defaultUniverse();
        this.symbolSearch = new SymbolSearch(universe);
    }

    /** Durable journal behind one round: flushed before history reads. */
    private record RoundDurability(com.virtualinvestor.persist.event.JdbcEventLog log,
            com.virtualinvestor.runtime.JournaledWorldJournal journal) {
    }

    public PlayerService players() {
        return players;
    }

    public InstrumentUniverse universe() {
        return universe;
    }

    public List<Instrument> search(String query, int limit) {
        return symbolSearch.search(query, limit);
    }

    /** Create a round with a fresh seed, participants and a session token for the player. */
    public RoundView createRound(LaunchConfig config) {
        return createRound(config, List.<String>of(), true,
                com.virtualinvestor.runtime.RunningRound.DEFAULT_STEP_MILLIS);
    }

    /**
     * Create a round for a registered player (player-accounts 4.1). Under the casual
     * daily-reset policy the purse is reset to the round's starting capital, and the
     * settlement write-back stores the result for the career policy to read later.
     */
    public RoundView createRound(LaunchConfig config, String playerId, boolean runInRealTime) {
        return createRound(config, playerId == null ? List.<String>of() : List.of(playerId),
                runInRealTime, com.virtualinvestor.runtime.RunningRound.DEFAULT_STEP_MILLIS);
    }

    /** As above with an explicit step size (tests use minute steps for short sessions). */
    public RoundView createRound(LaunchConfig config, String playerId, boolean runInRealTime, long stepMillis) {
        return createRound(config, playerId == null ? List.of() : List.of(playerId), runInRealTime,
                stepMillis);
    }

    /**
     * Create a round seating every listed player in order (multiplayer-seats 2.1):
     * the first takes "human", later ones "human-2" and so on; unclaimed seats
     * backfill with bots. Anonymous rounds keep the legacy single seat.
     */
    public RoundView createRound(LaunchConfig config, List<String> playerIds, boolean runInRealTime,
            long stepMillis) {
        long seed = 20261002L + roundCounter;
        String roundId = "round-" + (++roundCounter) + "-" + BOOT_TAG;
        Map<String, String> seats = new LinkedHashMap<>();
        List<HumanParticipant> extras = new ArrayList<>();
        for (int i = 0; i < playerIds.size(); i++) {
            String seat = i == 0 ? "human" : "human-" + (i + 1);
            seats.put(seat, playerIds.get(i));
            players.store().resetPurse(playerIds.get(i), config.startingCapital());
            if (i > 0) {
                String name = players.store().findById(playerIds.get(i)).map(PlayerStore.Player::username)
                        .orElse(seat);
                extras.add(new HumanParticipant(seat, name, config.startingCapital()));
            }
        }
        if (!seats.isEmpty()) {
            roundPlayers.put(roundId, new LinkedHashMap<>(seats));
        }
        RoundLifecycle lifecycle = new RoundLifecycle(roundId, seed, config);

        HumanParticipant player = new HumanParticipant("human", "You", config.startingCapital());
        List<BotParticipant> rivals = RivalRoster.rivals(config.startingCapital());
        List<BotParticipant> institutions = RivalRoster.institutions(config.startingCapital());

        Map<String, Double> startingCapitals = new LinkedHashMap<>();
        startingCapitals.put(player.id(), config.startingCapital());
        for (BotParticipant rival : rivals) {
            startingCapitals.put(rival.id(), config.startingCapital());
        }

        String rawToken = "session-" + roundId + "-" + seed;
        sessionTokens.put(player.id(), rawToken);

        RoundView view = new RoundView(roundId, seed, config, lifecycle, player, rivals, institutions,
                startingCapitals, rawToken);
        rounds.put(roundId, view);
        // Start the live world: warm-up, then the real-time loop streaming to subscribers.
        com.virtualinvestor.runtime.RunningRound live =
                runtime.start(roundId, seed, config, runInRealTime, stepMillis, extras);
        attachJournal(roundId, live);
        wireTariffs(live, seats);
        if (!seats.isEmpty()) {
            live.addListener(events -> writeBackPurse(roundId, events));
        }
        return view;
    }

    /**
     * Durable journal for the round (portfolio-tab 2.1): every tick's events land in
     * the round's event log through the batched writer, so the fill-history query
     * and future recovery read the same stream the world produced.
     */
    private void attachJournal(String roundId,
            com.virtualinvestor.runtime.RunningRound live) {
        com.virtualinvestor.persist.event.JdbcEventLog log =
                new com.virtualinvestor.persist.event.JdbcEventLog(dataSource, roundId);
        com.virtualinvestor.persist.event.BatchedEventLogWriter writer =
                new com.virtualinvestor.persist.event.BatchedEventLogWriter(log, 100);
        com.virtualinvestor.persist.session.RoundRecoveryService recovery =
                new com.virtualinvestor.persist.session.RoundRecoveryService(log,
                        new com.virtualinvestor.persist.snapshot.JdbcSnapshotStore(dataSource, roundId),
                        writer);
        com.virtualinvestor.runtime.JournaledWorldJournal journal =
                new com.virtualinvestor.runtime.JournaledWorldJournal(writer, recovery, 600L);
        durability.put(roundId, new RoundDurability(log, journal));
        live.setJournal(journal);
    }

    /**
     * Fill history for one participant (portfolio-tab 2.1): flush the journal, fold
     * the round's log, price fees under the participant's current bank schedule.
     */
    public List<FillHistory.FillRow> fills(String roundId, String participantId) {
        RoundDurability durable = durability.get(roundId);
        if (durable == null && runtime.round(roundId).isEmpty()) {
            throw new IllegalArgumentException("unknown round: " + roundId);
        }
        if (durable != null) {
            durable.journal().flush();
        }
        com.virtualinvestor.persist.event.JdbcEventLog log = durable != null ? durable.log()
                : new com.virtualinvestor.persist.event.JdbcEventLog(dataSource, roundId);
        com.virtualinvestor.runtime.FeeSchedule fees = runtime.round(roundId)
                .map(live -> live.feeScheduleOf(participantId))
                .orElse(com.virtualinvestor.runtime.FeeSchedule.none());
        return FillHistory.fold(log.readFrom(1L), participantId, fees);
    }

    /**
     * Per-fill markouts for one participant (realistic-liquidity 4.1): flush the
     * journal, fold the round's log, joining each fill to the first print at or
     * past fill tick + horizon. Non-positive horizons fall back to
     * {@link #DEFAULT_MARKOUT_HORIZON_TICKS}.
     */
    public static final long DEFAULT_MARKOUT_HORIZON_TICKS = 5L;

    public List<Markout.MarkoutRow> markouts(String roundId, String participantId, long horizonTicks) {
        RoundDurability durable = durability.get(roundId);
        if (durable == null && runtime.round(roundId).isEmpty()) {
            throw new IllegalArgumentException("unknown round: " + roundId);
        }
        if (durable != null) {
            durable.journal().flush();
        }
        com.virtualinvestor.persist.event.JdbcEventLog log = durable != null ? durable.log()
                : new com.virtualinvestor.persist.event.JdbcEventLog(dataSource, roundId);
        long horizon = horizonTicks <= 0L ? DEFAULT_MARKOUT_HORIZON_TICKS : horizonTicks;
        return Markout.fold(log.readFrom(1L), participantId, horizon);
    }

    /**
     * Bot parity (player-accounts 5.2, name-stable per multiplayer-seats D3): every
     * bot pays its bank's schedule, keyed by bot name so behavior never shifts with
     * headcount; each seated human pays theirs. Seats without a bank row pay nothing.
     */
    private void wireTariffs(com.virtualinvestor.runtime.RunningRound live, Map<String, String> seats) {
        List<String> bankIds = players.store().banks().stream()
                .map(com.virtualinvestor.persist.player.PlayerStore.Bank::bankId).toList();
        Map<String, PlayerStore.Player> bots = new LinkedHashMap<>();
        for (PlayerStore.Player bot : com.virtualinvestor.persist.player.BotSeeder.ensureRoster(
                players.store(), bankIds)) {
            bots.put(bot.username(), bot);
        }
        for (PlayerStore.Player bot : com.virtualinvestor.persist.player.BotSeeder.ensureInstitutions(
                players.store(), bankIds)) {
            bots.put(bot.username(), bot);
        }
        for (int i = 1; i <= 5; i++) {
            PlayerStore.Player bot = bots.get("bot-" + com.virtualinvestor.core.game.RivalRoster.RIVAL_NAMES
                    .get(i - 1).toLowerCase(java.util.Locale.ROOT));
            if (bot != null) {
                tariff(live, "rival-" + i, bot);
            }
        }
        List<String> instNames = com.virtualinvestor.core.game.RivalRoster.INSTITUTION_NAMES;
        for (int i = 0; i < instNames.size(); i++) {
            String username = "bot-" + instNames.get(i).toLowerCase(java.util.Locale.ROOT).replace(' ', '-');
            PlayerStore.Player bot = bots.get(username);
            if (bot != null) {
                tariff(live, "inst-" + (i + 1), bot);
            }
        }
        for (Map.Entry<String, String> seat : seats.entrySet()) {
            players.store().findById(seat.getValue())
                    .ifPresent(player -> tariff(live, seat.getKey(), player));
        }
    }

    private void tariff(com.virtualinvestor.runtime.RunningRound live, String seat, PlayerStore.Player player) {
        players.store().bank(player.bankId()).ifPresent(bank -> live.setFeeSchedule(seat,
                com.virtualinvestor.runtime.FeeSchedule.fixed(bank.feePerShare(), bank.ticketMinimum())));
    }

    /** Settlement write-back: every seated human's final account becomes their stored purse. */
    private void writeBackPurse(String roundId, java.util.List<com.virtualinvestor.core.event.GameEvent> events) {
        boolean settled = events.stream().anyMatch(com.virtualinvestor.core.event.GameEvent.RoundSettled.class::isInstance);
        if (!settled) {
            return;
        }
        Map<String, String> seats = roundPlayers.get(roundId);
        if (seats == null) {
            return;
        }
        runtime.round(roundId).ifPresent(live -> {
            for (Map.Entry<String, String> seat : seats.entrySet()) {
                com.virtualinvestor.core.participant.Account account;
                try {
                    account = live.participant(seat.getKey()).account();
                } catch (IllegalArgumentException unknown) {
                    continue;
                }
                Map<String, PlayerStore.Holding> holdings = new LinkedHashMap<>();
                for (com.virtualinvestor.core.participant.Position position : account.positions().values()) {
                    holdings.put(position.ticker(), new PlayerStore.Holding(position.ticker(), position.netQty(),
                            position.avgPrice(), position.realized()));
                }
                players.store().savePurse(seat.getValue(), account.cash(), account.feesPaid(), holdings);
            }
        });
    }

    /** All rounds this server created, for the game-master list. */
    public List<RoundView> rounds() {
        return List.copyOf(rounds.values());
    }

    /** The seat a player holds in a round, if the round seated them (multiplayer-seats 2.3). */
    public Optional<String> seatFor(String roundId, String playerId) {
        Map<String, String> seats = roundPlayers.get(roundId);
        if (seats == null) {
            return Optional.empty();
        }
        return seats.entrySet().stream()
                .filter(entry -> entry.getValue().equals(playerId))
                .map(Map.Entry::getKey)
                .findFirst();
    }

    /** Settle a round early at current marks (player-accounts 3.3). */
    public void settleEarly(String roundId) {
        com.virtualinvestor.runtime.RunningRound live = runtime.round(roundId)
                .orElseThrow(() -> new IllegalArgumentException("unknown round: " + roundId));
        live.settleNow();
    }

    /** Pause or resume a running round. */
    public void control(String roundId, String action) {
        if (rounds.get(roundId) == null) {
            throw new IllegalArgumentException("unknown round: " + roundId);
        }
        switch (action == null ? "" : action.toLowerCase(java.util.Locale.ROOT)) {
            case "pause" -> runtime.pause(roundId);
            case "resume" -> runtime.resume(roundId);
            default -> throw new IllegalArgumentException("unknown control action: " + action);
        }
    }

    /** Submit a client order command onto the running round. */
    public void submit(String roundId, String participantId, com.virtualinvestor.edge.wire.WireMessage.Payload payload) {
        long tick = runtime.round(roundId)
                .map(com.virtualinvestor.runtime.RunningRound::tick)
                .orElse(0L);
        runtime.submit(roundId, participantId, payload, tick);
    }

    public Optional<RoundView> round(String roundId) {
        return Optional.ofNullable(rounds.get(roundId));
    }

    /** The live runtime round, when it is running. */
    public Optional<com.virtualinvestor.runtime.RunningRound> runningRound(String roundId) {
        return runtime.round(roundId);
    }

    public List<Standing> leaderboard(String roundId, Map<String, Double> marks) {
        RoundView view = rounds.get(roundId);
        if (view == null) {
            return List.of();
        }
        // Once the run is live, rank the actual participants by their current equity.
        var live = runtime.round(roundId);
        if (live.isPresent()) {
            return Ranking.standings(live.get().participants(), live.get().marks(), view.startingCapitals());
        }
        List<Participant> everyone = new ArrayList<>();
        everyone.add(view.player());
        everyone.addAll(view.rivals());
        everyone.addAll(view.institutions());
        return Ranking.standings(everyone, marks, view.startingCapitals());
    }

    /** A round's live view, including its participants and current standings. */
    public static final class RoundView {
        private final String roundId;
        private final long seed;
        private final LaunchConfig config;
        private final RoundLifecycle lifecycle;
        private final HumanParticipant player;
        private final List<BotParticipant> rivals;
        private final List<BotParticipant> institutions;
        private final Map<String, Double> startingCapitals;
        private final String sessionToken;
        private final long durationTicks;

        RoundView(String roundId, long seed, LaunchConfig config, RoundLifecycle lifecycle,
                  HumanParticipant player, List<BotParticipant> rivals, List<BotParticipant> institutions,
                  Map<String, Double> startingCapitals, String sessionToken) {
            this.roundId = roundId;
            this.seed = seed;
            this.config = config;
            this.lifecycle = lifecycle;
            this.player = player;
            this.rivals = rivals;
            this.institutions = institutions;
            this.startingCapitals = startingCapitals;
            this.sessionToken = sessionToken;
            this.durationTicks = config.durationTicks(STEP_MILLIS);
        }

        public String roundId() {
            return roundId;
        }

        public long seed() {
            return seed;
        }

        public LaunchConfig config() {
            return config;
        }

        public RoundLifecycle lifecycle() {
            return lifecycle;
        }

        public HumanParticipant player() {
            return player;
        }

        public List<BotParticipant> rivals() {
            return rivals;
        }

        public List<BotParticipant> institutions() {
            return institutions;
        }

        public Map<String, Double> startingCapitals() {
            return startingCapitals;
        }

        public String sessionToken() {
            return sessionToken;
        }

        public long durationTicks() {
            return durationTicks;
        }
    }

    /** Expose the preset list for the launch pad. */
    public static List<RealismPreset> presets() {
        return List.of(RealismPreset.values());
    }
}
