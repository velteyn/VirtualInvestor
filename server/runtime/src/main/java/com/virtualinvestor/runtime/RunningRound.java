package com.virtualinvestor.runtime;

import com.virtualinvestor.core.auction.Auction;
import com.virtualinvestor.core.auction.AuctionFill;
import com.virtualinvestor.core.auction.AuctionOrder;
import com.virtualinvestor.core.auction.AuctionResult;
import com.virtualinvestor.core.book.IncomingOrder;
import com.virtualinvestor.core.bot.NewsSignal;
import com.virtualinvestor.core.clock.TimeSource;
import com.virtualinvestor.core.clock.WorldClock;
import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.domain.RoundState;
import com.virtualinvestor.core.domain.SessionPhase;
import com.virtualinvestor.core.engine.InstrumentEngine;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.fundamental.FundamentalModel;
import com.virtualinvestor.core.fundamental.VolatilityState;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.game.MarginManager;
import com.virtualinvestor.core.game.RivalRoster;
import com.virtualinvestor.core.game.RoundLifecycle;
import com.virtualinvestor.core.game.Settlement;
import com.virtualinvestor.core.game.WarmupGenerator;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.market.Bar;
import com.virtualinvestor.core.news.NewsEngine;
import com.virtualinvestor.core.news.NewsGenerator;
import com.virtualinvestor.core.news.NewsItem;
import com.virtualinvestor.core.news.PlannedNews;
import com.virtualinvestor.core.participant.BotParticipant;
import com.virtualinvestor.core.participant.HumanParticipant;
import com.virtualinvestor.core.participant.Participant;
import com.virtualinvestor.core.participant.TradeGateway;
import com.virtualinvestor.core.random.RandomStreams;
import com.virtualinvestor.core.rules.CircuitBreaker;
import com.virtualinvestor.core.rules.MarketRules;
import com.virtualinvestor.core.rules.OrderValidator;
import com.virtualinvestor.core.rules.ShortSalePolicy;
import com.virtualinvestor.core.rules.ValidationResult;
import com.virtualinvestor.core.session.SessionSchedule;
import com.virtualinvestor.core.session.SessionStateMachine;
import com.virtualinvestor.core.session.SessionTransition;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * The running round (design D1): composes the framework-free core into one deterministic world.
 * It owns the clock, the fundamental/news/bot simulation, the session, auctions and margin
 * rules, the participant roster and the trade gateway, drains client commands at the top of
 * each tick, and publishes the events it produces to listeners and the durable journal.
 *
 * <p>Single-writer: {@link #step()} runs on one thread; {@link #enqueue(Command)} is the only
 * cross-thread entry.
 */
public final class RunningRound {

    public static final long DEFAULT_STEP_MILLIS = 100L;
    private static final long YEAR_MILLIS = 5_896_800_000L;
    private static final double MARKET_ANNUAL_VOL = 0.30;
    private static final double SECTOR_ANNUAL_VOL = 0.20;
    private static final double VOLATILITY_DECAY = 0.99;
    private static final long QUOTE_REFRESH_TICKS = 50L;
    private static final long RANDOM_NEWS_TICKS = 1500L;
    // Order-flow impact and reversion. Coefficients are per unit flow divided by the
    // instrument's tradeable float (shares), so ~100 shares moves a name ~0.05% permanently
    // and ~0.2% transiently. Tuned via docs/calibration.md (ImpactCalibration).
    private static final double PERMANENT_IMPACT = 5.0;
    private static final double TRANSIENT_IMPACT = 20.0;
    private static final double TRANSIENT_DECAY = 0.97;
    private static final double REVERSION_STRENGTH = 0.02;
    private static final double TOXICITY_FLOW_WEIGHT = 500.0;
    private static final double TOXICITY_VOL_WEIGHT = 0.25;
    private static final double TOXICITY_DECAY = 0.97;
    /** A bot stops and is flattened once its equity falls to this fraction of starting capital. */
    private static final double RISK_FLOOR_FRACTION = 0.20;

    private final String roundId;
    private final long seed;
    private final LaunchConfig config;
    private final long stepMillis;
    private final InstrumentUniverse universe;
    private final WorldClock clock;
    private final FundamentalModel fundamentals;
    private final VolatilityState volatility;
    private final com.virtualinvestor.core.fundamental.FlowToxicity toxicity;
    private final Map<String, InstrumentEngine> engines;
    private final NewsEngine news;
    private final SessionSchedule schedule;
    private final SessionStateMachine session;
    private final MarginManager margin;
    private final RoundLifecycle lifecycle;
    private final WarmupGenerator warmup;
    private final TradeGateway gateway;
    private final CommandQueue commands;
    private final HumanParticipant player;
    /** All humans in join order (player first); mutable pre-start for room joins. */
    private final List<com.virtualinvestor.core.participant.HumanParticipant> humans = new ArrayList<>();
    private final List<com.virtualinvestor.core.participant.HumanParticipant> extraHumans = new ArrayList<>();
    private List<BotParticipant> rivals;
    private final List<BotParticipant> institutions;
    private final List<Participant> participants = new ArrayList<>();
    private List<BotRunner> runners;
    private final long sessionTicks;
    /** Ticker → sector, for routing sector news to its symbols. */
    private final Map<String, String> sectorByTicker = new LinkedHashMap<>();
    /** Index ticker: the only engine macro headlines reach. */
    private String indexTicker = "VNDX";
    private final Map<String, Double> previousPrices;
    private final Map<String, Double> startingCapitals;
    private final Map<String, MarketRules> marketRules = new LinkedHashMap<>();
    private final Map<String, CircuitBreaker> breakers = new LinkedHashMap<>();
    private final Map<String, ShortSalePolicy> shortPolicies = new LinkedHashMap<>();
    private final List<Consumer<List<GameEvent>>> listeners = new ArrayList<>();
    private final java.util.Set<String> processedCommands = new java.util.LinkedHashSet<>();
    /** Net aggressive flow accumulated this tick, per instrument (positive buy, negative sell). */
    private final Map<String, Double> netFlow = new LinkedHashMap<>();
    /** Cumulative net flow since the round began, per instrument (for observation/tuning). */
    private final Map<String, Double> cumulativeFlow = new LinkedHashMap<>();
    /**
     * Live-bar aggregation (task 2.2): trade prints accumulate into the open 60-tick window
     * per symbol — the same window the warm-up history bars cover — and a {@code BarClosed}
     * is emitted when the window rolls. Bar times continue the history axis so the chart's
     * warm-up bars and live bars share one time scale.
     */
    static final long BAR_TICKS = 60L;
    private final Map<String, BarAccumulator> barWindows = new LinkedHashMap<>();
    private final long barSeconds;
    private final Map<String, Long> barBaseSec = new LinkedHashMap<>();

    /** One forming OHLCV window. */
    private static final class BarAccumulator {
        final long window;
        double open;
        double high = Double.NEGATIVE_INFINITY;
        double low = Double.POSITIVE_INFINITY;
        double close;
        long volume;

        BarAccumulator(long window, double price, long quantity) {
            this.window = window;
            this.open = price;
            print(price, quantity);
        }

        void print(double price, long quantity) {
            high = Math.max(high, price);
            low = Math.min(low, price);
            close = price;
            volume += quantity;
        }
    }

    private WorldJournal journal = WorldJournal.noop();
    private EventPublisher publisher = EventPublisher.noop();
    private long sequence;
    private boolean warmedUp;
    private Map<String, List<Bar>> history = Map.of();
    private Map<String, Auction> activeAuctions = new LinkedHashMap<>();
    private boolean settled;

    public RunningRound(String roundId, long seed, LaunchConfig config) {
        this(roundId, seed, config, DEFAULT_STEP_MILLIS);
    }

    public RunningRound(String roundId, long seed, LaunchConfig config, long stepMillis) {
        this(roundId, seed, config, stepMillis, List.of());
    }

    /**
     * Round with extra human seats (multiplayer-seats 1.1): each extra human trades
     * as a distinct participant funded equally. Ranked rivals backfill the seats
     * humans leave empty (first-N by roster); solo games are unchanged.
     */
    public RunningRound(String roundId, long seed, LaunchConfig config, long stepMillis,
            List<com.virtualinvestor.core.participant.HumanParticipant> extraHumans) {
        this(roundId, seed, config, stepMillis, extraHumans, "You");
    }

    /**
     * As above with a display name for the first human seat (game-lobby 2.2):
     * rooms show the master's username, solo games keep "You".
     */
    public RunningRound(String roundId, long seed, LaunchConfig config, long stepMillis,
            List<com.virtualinvestor.core.participant.HumanParticipant> extraHumans, String playerName) {
        if (extraHumans.size() > 5) {
            throw new IllegalArgumentException("at most 5 extra humans fit the 6 ranked seats");
        }
        this.roundId = Objects.requireNonNull(roundId, "roundId");
        this.seed = seed;
        this.config = Objects.requireNonNull(config, "config");
        if (stepMillis <= 0L) {
            throw new IllegalArgumentException("stepMillis must be positive");
        }
        this.stepMillis = stepMillis;
        this.barSeconds = Math.max(1L, BAR_TICKS * stepMillis / 1_000L);

        long durationTicks = config.durationTicks(stepMillis);
        this.universe = InstrumentUniverse.defaultUniverse();
        for (Instrument instrument : universe.all()) {
            sectorByTicker.put(instrument.ticker(), instrument.sector());
            if (instrument.isIndex()) {
                indexTicker = instrument.ticker();
            }
        }
        this.clock = new WorldClock(stepMillis, TimeSource.system());
        RandomStreams streams = new RandomStreams(seed);
        this.fundamentals = new FundamentalModel(
                universe, streams, MARKET_ANNUAL_VOL, SECTOR_ANNUAL_VOL, stepMillis, YEAR_MILLIS);
        this.volatility = new VolatilityState(VOLATILITY_DECAY);
        fundamentals.setVolatilityState(volatility);
        // Order-flow impact (design D1-D4): trading moves the latent fundamental, scaled by
        // liquidity (free float), with a reversion pull so the market stays tethered.
        // Liquidity is the tradeable size a normal order is measured against, in shares: a
        // fraction of the free float. This keeps the liquidity scale near 1 for typical
        // instruments, so a few hundred shares move an illiquid name more than a liquid one.
        Map<String, Double> liquidity = new LinkedHashMap<>();
        for (Instrument instrument : universe.all()) {
            double tradeable = Math.max(10_000.0, instrument.freeFloat() * 1_000_000.0);
            liquidity.put(instrument.ticker(), tradeable);
        }
        fundamentals.setOrderFlowImpact(new com.virtualinvestor.core.fundamental.OrderFlowImpact(
                PERMANENT_IMPACT, TRANSIENT_IMPACT, TRANSIENT_DECAY, liquidity));
        fundamentals.setReversionStrength(REVERSION_STRENGTH);
        this.toxicity = new com.virtualinvestor.core.fundamental.FlowToxicity(
                TOXICITY_FLOW_WEIGHT, TOXICITY_VOL_WEIGHT, TOXICITY_DECAY, liquidity);

        this.engines = new LinkedHashMap<>();
        this.previousPrices = new LinkedHashMap<>();
        for (Instrument instrument : universe.all()) {
            engines.put(instrument.ticker(), new InstrumentEngine(instrument.ticker(), instrument.referencePrice()));
            previousPrices.put(instrument.ticker(), instrument.referencePrice());
            marketRules.put(instrument.ticker(), MarketRules.forInstrument(instrument));
            CircuitBreaker breaker = config.preset().newCircuitBreaker(instrument.referencePrice());
            if (breaker != null) {
                breakers.put(instrument.ticker(), breaker);
            }
            // Equities are shortable; the index is not.
            shortPolicies.put(instrument.ticker(), config.preset().newShortSalePolicy(!instrument.isIndex()));
        }

        List<NewsItem> timeline = new NewsGenerator(universe, streams, RANDOM_NEWS_TICKS, 0.30)
                .generate(0L, durationTicks);
        List<PlannedNews> planned = new ArrayList<>(timeline.size());
        for (int i = 0; i < timeline.size(); i++) {
            NewsItem item = timeline.get(i);
            planned.add(new PlannedNews(item, item.tick() + durationTicks / 10L, i % 2 == 0, 0.5));
        }
        this.news = new NewsEngine(fundamentals, planned);
        news.setVolatilityState(volatility);

        this.schedule = SessionSchedule.standard(durationTicks);
        this.session = new SessionStateMachine(schedule, 0L);
        this.margin = new MarginManager(config.preset().maintenanceMarginFraction());
        this.lifecycle = new RoundLifecycle(roundId, seed, config);
        this.warmup = new WarmupGenerator(
                new com.virtualinvestor.core.bot.MarketMakerStrategy(0.02, 2.0, 0.001, 500L), stepMillis, 1_000_000.0);
        this.gateway = new TradeGateway();
        for (InstrumentEngine engine : engines.values()) {
            gateway.register(engine);
        }
        this.commands = new CommandQueue();

        this.player = new HumanParticipant("human", playerName, config.startingCapital());
        humans.add(player);
        humans.addAll(extraHumans);
        this.sessionTicks = durationTicks;
        this.institutions = RivalRoster.institutions(config.startingCapital());

        this.startingCapitals = new LinkedHashMap<>();
        rebuildRoster();
    }

    /**
     * Rebuild the ranked roster from the current humans (game-lobby 1.2): rivals
     * backfill first-N, so each join yields the last rival. Pre-start only — nothing
     * has ticked, so fresh runners are identical to constructor-built ones.
     */
    private void rebuildRoster() {
        extraHumans.clear();
        for (com.virtualinvestor.core.participant.HumanParticipant human : humans) {
            if (!human.id().equals(player.id())) {
                extraHumans.add(human);
            }
        }
        rivals = new ArrayList<>(
                RivalRoster.rivals(config.startingCapital()).subList(0, Math.max(0, 6 - humans.size())));
        participants.clear();
        participants.addAll(humans);
        participants.addAll(rivals);
        participants.addAll(institutions);
        for (Participant participant : participants) {
            startingCapitals.putIfAbsent(participant.id(), config.startingCapital());
        }
        runners = buildRunners(sessionTicks);
    }

    /** Hold the built world for a room: the clock stays stopped until master start. */
    public void hold() {
        lifecycle.transitionTo(RoundState.WAITING);
    }

    /**
     * Claim a ranked seat before the clock runs (game-lobby 1.2): appends the human
     * and yields the last roster rival. Refused once ticked and when full.
     */
    public void joinHuman(com.virtualinvestor.core.participant.HumanParticipant human) {
        RoundState state = lifecycle.state();
        if (state != RoundState.CREATED && state != RoundState.WAITING) {
            throw new IllegalStateException("roster is locked once the clock runs: state=" + state);
        }
        if (humans.size() >= 6) {
            throw new IllegalStateException("room is full: 6 ranked seats claimed");
        }
        humans.add(Objects.requireNonNull(human, "human"));
        rebuildRoster();
    }

    /**
     * Route one tick's releases to per-symbol headline signals (news-reading-bots 1.1):
     * company news reaches its symbol, sector news its symbols, macro (and structural)
     * news the index only — so one headline can never fire every engine at once.
     * Pure fold of the release events, so replays agree.
     */
    static Map<String, NewsSignal> routeReleases(List<GameEvent> newsEvents,
            Map<String, String> sectorBySymbol, String indexTicker) {
        Map<String, NewsSignal> signals = new LinkedHashMap<>();
        for (GameEvent event : newsEvents) {
            if (!(event instanceof GameEvent.NewsReleased release)) {
                continue;
            }
            NewsSignal signal = new NewsSignal(release.magnitude(), release.symbol(),
                    release.sector(), release.scope(), release.tick(), release.durationTicks());
            switch (release.scope()) {
                case COMPANY -> {
                    if (release.symbol() != null) {
                        signals.put(release.symbol(), signal);
                    }
                }
                case SECTOR -> sectorBySymbol.forEach((ticker, sector) -> {
                    if (sector != null && sector.equals(release.sector())) {
                        signals.put(ticker, signal);
                    }
                });
                default -> signals.put(indexTicker, signal);
            }
        }
        return signals;
    }

    /** One maker's toxicity response: sensitivity rises and tolerance falls by roster index. */
    private static com.virtualinvestor.core.bot.MarketMakerStrategy makerFor(int index) {
        return new com.virtualinvestor.core.bot.MarketMakerStrategy(0.02, 2.0, 0.001, 500L, 5, 2.0,
                1.0 + 0.5 * index, 4.0 - 0.25 * index);
    }

    private List<BotRunner> buildRunners(long sessionTicks) {
        List<BotRunner> built = new ArrayList<>();
        // Strategies key off bot names, not roster positions, so backfilled subsets
        // behave identically to the same bots in a full roster. Refresh phases
        // stagger by roster index so books don't all move on the same tick.
        for (int i = 0; i < rivals.size(); i++) {
            BotParticipant rival = rivals.get(i);
            built.add(switch (rival.displayName()) {
                case "MARK" -> new BotRunner(rival, null, makerFor(i), QUOTE_REFRESH_TICKS, tickFor("NVDO"), i,
                        sessionTicks, seed);
                case "TIDE" -> new BotRunner(rival,
                        new com.virtualinvestor.core.bot.MomentumStrategy(0.0005, 30L), null, 0L,
                        0.01, 0L, sessionTicks, seed);
                case "LEDGER" -> new BotRunner(rival,
                        new com.virtualinvestor.core.bot.ValueStrategy(0.005, 30L), null, 0L,
                        0.01, 0L, sessionTicks, seed);
                case "BLITZ" -> new BotRunner(rival,
                        new com.virtualinvestor.core.bot.ScalperStrategy(0.0005, 20L), null, 0L,
                        0.01, 0L, sessionTicks, seed);
                case "VULTURE" -> new BotRunner(rival,
                        new com.virtualinvestor.core.bot.NewsHunterStrategy(0.01, 40L), null, 0L,
                        0.01, 0L, sessionTicks, seed);
                default -> throw new IllegalArgumentException("unknown rival: " + rival.displayName());
            });
        }
        // Institutions: ATLAS and CROWN provide liquidity; MERIDIAN trades macro news.
        built.add(new BotRunner(institutions.get(0), null, makerFor(rivals.size()), QUOTE_REFRESH_TICKS,
                tickFor("NVDO"), rivals.size(), sessionTicks, seed));
        built.add(new BotRunner(institutions.get(1),
                new com.virtualinvestor.core.bot.MacroReactionStrategy(0.01, 1_000L), null, 0L,
                0.01, 0L, sessionTicks, seed));
        built.add(new BotRunner(institutions.get(2), null, makerFor(rivals.size() + 2),
                QUOTE_REFRESH_TICKS, tickFor("NVDO"), rivals.size() + 2, sessionTicks, seed));
        return List.copyOf(built);
    }

    /** The tick size for an instrument, used to snap market-maker quotes onto the grid. */
    private double tickFor(String symbol) {
        return universe.find(symbol).map(Instrument::tickSize).orElse(0.01);
    }

    public void setJournal(WorldJournal journal) {
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    public void setPublisher(EventPublisher publisher) {
        this.publisher = Objects.requireNonNull(publisher, "publisher");
    }

    /**
     * A safe driver entry point: advances one tick, silently doing nothing when the round has
     * settled or is not running (so a scheduled driver does not throw at the close).
     */
    public void stepQuietly() {
        if (settled || clock.state() != com.virtualinvestor.core.clock.WorldClock.State.RUNNING) {
            return;
        }
        step();
    }

    public void addListener(Consumer<List<GameEvent>> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void enqueue(Command command) {
        commands.enqueue(command);
    }

    /**
     * Rebuild a round from a persisted event history (design D5), for restart recovery. The
     * history is replayed through {@link #step()} from a fresh warm-up so prices, fills and
     * accounts land where the original run left them. Only the tick count and running/paused
     * state need reconstructing; the seed and config reproduce everything else.
     *
     * @param paused whether the round was paused when it stopped
     */
    public void recover(long recoveredTick, boolean paused) {
        start();
        while (clock.tick() < recoveredTick && !settled) {
            step();
        }
        if (paused) {
            clock.pause();
            lifecycle.transitionTo(RoundState.PAUSED);
        }
    }

    /** Begin the round: enter warm-up, generate history and seed books, then run. */
    public void start() {
        lifecycle.transitionTo(RoundState.WARMUP);
        if (!warmedUp) {
            history = warmup.generateHistory(universe, fundamentals, 60L, 60L, 1_700_000_000L);
            // Open each instrument at its warmed-up fundamental so books and circuit breakers
            // start consistent with the market rather than at the stale reference price.
            for (Instrument instrument : universe.all()) {
                String symbol = instrument.ticker();
                double open = fundamentals.fundamental(symbol);
                engines.get(symbol).setLastPrice(open);
                previousPrices.put(symbol, open);
                CircuitBreaker breaker = breakers.get(symbol);
                if (breaker != null) {
                    breakers.put(symbol, config.preset().newCircuitBreaker(open));
                }
            }
            warmup.seedBooks(engines.values(), fundamentals,
                    rivals.isEmpty() ? institutions.get(0).id() : rivals.get(0).id());
            warmedUp = true;
            // Anchor the live-bar axis on the history it continues: live window w closes at
            // lastHistoryTime + (w + 1) * barSeconds.
            for (Map.Entry<String, List<Bar>> entry : history.entrySet()) {
                List<Bar> bars = entry.getValue();
                if (!bars.isEmpty()) {
                    barBaseSec.put(entry.getKey(), bars.get(bars.size() - 1).timeSec());
                }
            }
        }
        session.begin();
        lifecycle.transitionTo(RoundState.RUNNING);
        clock.start();
    }

    /**
     * Advance the world exactly one tick and return the events it produced. The order is fixed
     * and documented: (1) apply queued client commands, (2) advance fundamentals and
     * volatility, (3) advance the session phase (opening/closing auctions run at boundaries),
     * (4) release due news, (5) let the bots act during continuous trading, (6) enforce margin.
     */
    public List<GameEvent> step() {
        if (settled) {
            throw new IllegalStateException("round is settled");
        }
        if (!clock.step()) {
            throw new IllegalStateException("round is not running: state=" + lifecycle.state());
        }
        List<GameEvent> events = new ArrayList<>();
        long tick = clock.tick();

        for (Command command : commands.drain()) {
            events.addAll(applyCommand(command, tick));
        }

        fundamentals.step();
        volatility.step();
        events.add(new GameEvent.Tick(tick, clock.logicalMillis()));

        // Order-flow impact: apply the flow accumulated since the last tick (player commands
        // plus the previous tick's bot trades), then clear it. Bots this tick see and quote
        // around the moved fundamental.
        for (Map.Entry<String, Double> entry : netFlow.entrySet()) {
            if (entry.getValue() != 0.0) {
                fundamentals.applyFlow(entry.getKey(), entry.getValue());
            }
        }
        // Toxicity sees every symbol every tick (zero flow still decays the score).
        for (Instrument instrument : universe.all()) {
            String symbol = instrument.ticker();
            toxicity.observe(symbol, netFlow.getOrDefault(symbol, 0.0),
                    Math.max(0.0, volatility.multiplier(symbol) - 1.0));
        }
        netFlow.clear();


        for (SessionTransition transition : session.advance(tick)) {
            handleTransition(transition, tick, events);
        }

        List<GameEvent> newsEvents = news.advance(tick);
        events.addAll(newsEvents);
        Map<String, NewsSignal> signals = routeReleases(newsEvents, sectorByTicker, indexTicker);

        if (session.phase() == SessionPhase.CONTINUOUS) {
            for (Instrument instrument : universe.all()) {
                String symbol = instrument.ticker();
                InstrumentEngine engine = engines.get(symbol);
                double fundamental = fundamentals.fundamental(symbol);
                double vol = volatility.multiplier(symbol) - 1.0;
                double change = priceChange(symbol, engine.lastPrice());
                Map<String, Double> liveMarks = marks();
                for (BotRunner runner : runners) {
                    // Risk floor: stop and flatten a participant whose equity has hit the floor.
                    double equity = runner.participant().account().equity(liveMarks);
                    if (!runner.stopped() && equity <= config.startingCapital() * RISK_FLOOR_FRACTION) {
                        runner.stop();
                        events.addAll(com.virtualinvestor.core.game.MarginManager.forceClose(
                                runner.participant(), liveMarks, tick));
                        continue;
                    }
                    long netPosition = runner.participant().account().position(symbol).netQty();
                    events.addAll(runner.act(engine, fundamental, vol, change, netPosition, tick, equity,
                            toxicity(symbol), signals.get(symbol),
                            fill -> applyFill(fill, symbol, events, tick)));
                }
            }
        }

        for (Instrument instrument : universe.all()) {
            CircuitBreaker breaker = breakers.get(instrument.ticker());
            if (breaker != null) {
                breaker.onPrice(engines.get(instrument.ticker()).lastPrice(), tick);
            }
        }

        Map<String, Double> marks = marks();
        for (Participant participant : participants) {
            events.addAll(margin.enforce(participant, marks, tick));
        }

        events.addAll(formBarEvents(events, tick));

        publish(events);
        return List.copyOf(events);
    }

    /**
     * Fold the step's own trade prints into the open bar windows and close every window
     * the tick has rolled past. A window with no prints stays open until its first print,
     * so a closed bar always reflects real prints, never synthesis.
     */
    private List<GameEvent> formBarEvents(List<GameEvent> events, long tick) {
        long currentWindow = tick / BAR_TICKS;
        List<GameEvent> bars = new ArrayList<>();
        for (GameEvent event : events) {
            if (event instanceof GameEvent.TradePrinted trade) {
                long window = trade.tick() / BAR_TICKS;
                BarAccumulator acc = barWindows.get(trade.symbol());
                if (acc != null && acc.window != window) {
                    // The print rolled into a new window: close the old one first so no
                    // prints are ever dropped, even for symbols that trade sporadically.
                    bars.add(closedBar(trade.symbol(), acc));
                    acc = null;
                }
                if (acc == null) {
                    barWindows.put(trade.symbol(), new BarAccumulator(window, trade.price(), trade.quantity()));
                } else {
                    acc.print(trade.price(), trade.quantity());
                }
            }
        }
        var iterator = barWindows.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            BarAccumulator acc = entry.getValue();
            if (acc.window < currentWindow) {
                bars.add(closedBar(entry.getKey(), acc));
                iterator.remove();
            }
        }
        return bars;
    }

    /** A closed window as the wire-ready bar event, timed on the history axis. */
    private GameEvent.BarClosed closedBar(String symbol, BarAccumulator acc) {
        long base = barBaseSec.getOrDefault(symbol, 1_700_000_000L);
        return new GameEvent.BarClosed(symbol, barInterval(), base + (acc.window + 1L) * barSeconds,
                acc.open, acc.high, acc.low, acc.close, acc.volume, acc.window * BAR_TICKS);
    }

    private String barInterval() {
        return barSeconds >= 60L ? (barSeconds / 60L) + "m" : barSeconds + "s";
    }

    private void handleTransition(SessionTransition transition, long tick, List<GameEvent> events) {
        if (transition.to() == SessionPhase.OPENING_AUCTION || transition.to() == SessionPhase.CLOSING_AUCTION) {
            beginAuction(transition.to(), tick, events);
        }
        if (transition.from() == SessionPhase.OPENING_AUCTION && transition.to() != SessionPhase.OPENING_AUCTION) {
            clearAuction(SessionPhase.OPENING_AUCTION, false, tick, events);
        }
        if (transition.from() == SessionPhase.CLOSING_AUCTION && transition.to() != SessionPhase.CLOSING_AUCTION) {
            clearAuction(SessionPhase.CLOSING_AUCTION, true, tick, events);
        }
        if (transition.to() == SessionPhase.CONTINUOUS) {
            events.add(new GameEvent.SessionOpened(roundId, tick));
        }
        if (transition.to() == SessionPhase.CLOSING_AUCTION) {
            lifecycle.transitionTo(RoundState.CLOSING);
        }
        if (transition.to() == SessionPhase.CLOSED) {
            events.add(new GameEvent.SessionClosed(roundId, tick));
        }
    }

    private void beginAuction(SessionPhase phase, long tick, List<GameEvent> events) {
        activeAuctions = new LinkedHashMap<>();
        for (Instrument instrument : universe.all()) {
            activeAuctions.put(instrument.ticker(), new Auction());
            events.add(new GameEvent.AuctionOpened(instrument.ticker(), phase, tick));
        }
    }

    private void clearAuction(SessionPhase phase, boolean settleAfter, long tick, List<GameEvent> events) {
        for (Instrument instrument : universe.all()) {
            String symbol = instrument.ticker();
            Auction auction = activeAuctions.get(symbol);
            if (auction == null) {
                continue;
            }
            InstrumentEngine engine = engines.get(symbol);
            AuctionResult result = auction.clear(engine.lastPrice());
            if (result.traded()) {
                engine.setLastPrice(result.clearingPrice());
                for (AuctionFill fill : result.fills()) {
                    creditFill(fill.participantId(), symbol, fill.side(), fill.quantity(), fill.price(),
                            events, tick);
                }
            }
            events.add(new GameEvent.AuctionClosed(
                    symbol, phase, result.clearingPrice(), result.matchedVolume(), tick));
        }
        activeAuctions = new LinkedHashMap<>();
        if (settleAfter) {
            settle(tick, events);
        }
    }

    private void settle(long tick, List<GameEvent> events) {
        Settlement.Result result = Settlement.settle(participants, marks(), startingCapitals, tick, roundId);
        events.addAll(result.events());
        lifecycle.transitionTo(RoundState.SETTLED);
        settled = true;
        clock.stop();
    }

    /**
     * Game-master early settlement (player-accounts 3.3): close the round now at
     * current marks and publish the settlement events. No-op once settled.
     */
    public List<GameEvent> settleNow() {
        if (settled) {
            return List.of();
        }
        // Honor the lifecycle ladder (RUNNING -> CLOSING -> SETTLED); no step runs
        // between these transitions, so the round simply closes at current marks.
        if (lifecycle.state() == RoundState.PAUSED) {
            lifecycle.transitionTo(RoundState.RUNNING);
        }
        if (lifecycle.state() == RoundState.RUNNING) {
            lifecycle.transitionTo(RoundState.CLOSING);
        }
        List<GameEvent> events = new ArrayList<>();
        settle(clock.tick(), events);
        publish(events);
        return List.copyOf(events);
    }

    /** Credit both counterparties of a fill and record its aggressive flow. */
    private void applyFill(com.virtualinvestor.core.book.Fill fill, String symbol, List<GameEvent> events,
            long tick) {
        creditFill(fill.takerParticipantId(), symbol, fill.takerSide(), fill.quantity(), fill.price(),
                events, tick);
        creditFill(fill.makerParticipantId(), symbol, fill.makerSide(), fill.quantity(), fill.price(),
                events, tick);
        // The taker is the aggressor: buy flow positive, sell flow negative.
        double signed = fill.takerSide() == com.virtualinvestor.core.domain.OrderSide.BUY
                ? fill.quantity() : -fill.quantity();
        netFlow.merge(symbol, signed, Double::sum);
        cumulativeFlow.merge(symbol, signed, Double::sum);
    }

    /** Credit one counterparty of a fill and publish its position and account. */
    private void creditFill(String participantId, String symbol,
            com.virtualinvestor.core.domain.OrderSide side, long quantity, double price,
            List<GameEvent> events, long tick) {
        com.virtualinvestor.core.participant.Account account = participant(participantId).account();
        account.applyFill(symbol, side, quantity, price);
        // Broker tariffs (player-accounts 5.1): each counterparty pays its own bank's
        // schedule on the fill, deducted from cash and accumulated visibly as fees paid.
        account.debitFee(feesFor(participantId).feeFor(quantity));
        com.virtualinvestor.core.participant.Position position = account.position(symbol);
        events.add(new GameEvent.PositionUpdated(participantId, symbol, position.netQty(),
                position.avgPrice(), position.realized(), tick));
        events.add(new GameEvent.AccountUpdated(participantId, account.cash(), account.realizedPnl(),
                account.feesPaid(), tick));
    }

    private static List<GameEvent> rejected(Command.PlaceOrder place, String reason, long tick) {
        return List.of(new GameEvent.OrderReport(
                place.commandId(), place.participantId(), place.symbol(), place.side(), place.type(),
                place.quantity(), place.price(), 0L, place.quantity(),
                com.virtualinvestor.core.domain.OrderStatus.REJECTED,
                com.virtualinvestor.core.domain.OrderEventKind.REJECTED, reason, tick));
    }

    private List<GameEvent> applyCommand(Command command, long tick) {
        // Idempotency: a retried command with the same id is applied at most once.
        if (!processedCommands.add(command.commandId())) {
            return List.of();
        }
        if (command instanceof Command.PlaceOrder place) {
            SessionPhase phase = session.phase();
            if (phase == SessionPhase.OPENING_AUCTION || phase == SessionPhase.CLOSING_AUCTION) {
                Auction auction = activeAuctions.get(place.symbol());
                if (auction != null) {
                    auction.submit(new AuctionOrder(
                            place.commandId(), place.participantId(), place.side(), place.quantity(),
                            place.price(), place.type() == OrderType.MARKET));
                    return List.of(new GameEvent.OrderReport(
                            place.commandId(), place.participantId(), place.symbol(), place.side(), place.type(),
                            place.quantity(), place.price(), 0L, place.quantity(),
                            com.virtualinvestor.core.domain.OrderStatus.WORKING,
                            com.virtualinvestor.core.domain.OrderEventKind.NEW, null, tick));
                }
            }
            if (phase != SessionPhase.CONTINUOUS) {
                return rejected(place, "market is not in continuous trading", tick);
            }
            CircuitBreaker breaker = breakers.get(place.symbol());
            if (breaker != null && breaker.isHalted(tick)) {
                return rejected(place, "instrument is halted", tick);
            }
            ValidationResult validation = OrderValidator.validate(
                    marketRules.get(place.symbol()), place.side(), place.type(), place.price(), place.quantity());
            if (!validation.valid()) {
                return rejected(place, validation.reason(), tick);
            }
            long netPosition = participant(place.participantId()).account().position(place.symbol()).netQty();
            ValidationResult shorting = shortPolicies.get(place.symbol())
                    .check(place.side(), netPosition, place.quantity());
            if (!shorting.valid()) {
                return rejected(place, shorting.reason(), tick);
            }
            InstrumentEngine engine = engines.get(place.symbol());
            InstrumentEngine.SubmitResult result = engine.submitWithFills(new IncomingOrder(
                    place.commandId(), place.participantId(), place.symbol(), place.side(), place.type(),
                    place.quantity(), place.price()), tick);
            List<GameEvent> events = new ArrayList<>(result.events());
            for (com.virtualinvestor.core.book.Fill fill : result.fills()) {
                applyFill(fill, place.symbol(), events, tick);
            }
            return events;
        }
        if (command instanceof Command.CancelOrder cancel) {
            for (InstrumentEngine engine : engines.values()) {
                List<GameEvent> events = engine.cancel(cancel.orderId(), tick);
                if (!events.isEmpty()) {
                    return events;
                }
            }
            return List.of();
        }
        return List.of();
    }

    /** Lookup by id for snapshots and the edge resync path. */
    /** Current flow toxicity per symbol (realistic-liquidity 2.1); makers read it in 2.2. */
    public double toxicity(String symbol) {
        return toxicity.toxicity(symbol);
    }

    /** Fee tariff per participant; absent means no bank, no fees. */
    private final Map<String, FeeSchedule> feeSchedules = new LinkedHashMap<>();

    public void setFeeSchedule(String participantId, FeeSchedule schedule) {
        feeSchedules.put(participantId, java.util.Objects.requireNonNull(schedule, "schedule"));
    }

    private FeeSchedule feesFor(String participantId) {
        return feeSchedules.getOrDefault(participantId, FeeSchedule.none());
    }

    /** The tariff a seat traded under (portfolio-tab 2.1 prices history rows with it). */
    public FeeSchedule feeScheduleOf(String participantId) {
        return feesFor(participantId);
    }

    public Participant participant(String id) {
        for (Participant candidate : participants) {
            if (candidate.id().equals(id)) {
                return candidate;
            }
        }
        throw new IllegalArgumentException("unknown participant: " + id);
    }

    private double priceChange(String symbol, double price) {
        double previous = previousPrices.get(symbol);
        previousPrices.put(symbol, price);
        return previous == 0.0 ? 0.0 : (price - previous) / previous;
    }

    private void publish(List<GameEvent> events) {
        journal.append(events);
        journal.checkpoint(clock.tick());
        sequence += events.size();
        publisher.publish(events, sequence);
        for (Consumer<List<GameEvent>> listener : listeners) {
            listener.accept(events);
        }
    }

    public Map<String, Double> marks() {
        Map<String, Double> marks = new LinkedHashMap<>();
        for (Instrument instrument : universe.all()) {
            marks.put(instrument.ticker(), engines.get(instrument.ticker()).lastPrice());
        }
        return marks;
    }

    public Map<String, List<Bar>> history() {
        return history;
    }

    public Map<String, CircuitBreaker> breakers() {
        return breakers;
    }

    /** The current net aggressive flow accrued this tick (for observation/tests). */
    public Map<String, Double> netFlow() {
        return Map.copyOf(netFlow);
    }

    /** Cumulative net aggressive flow since the round began (for observation/tuning). */
    public Map<String, Double> cumulativeFlow() {
        return Map.copyOf(cumulativeFlow);
    }

    /** The gap between an instrument's fundamental and its reference (impact vs anchor). */
    public double fundamentalGap(String symbol) {
        return fundamentals.fundamental(symbol) - fundamentals.reference(symbol);
    }

    public Map<String, MarketRules> marketRules() {
        return marketRules;
    }

    public Map<String, ShortSalePolicy> shortPolicies() {
        return shortPolicies;
    }

    // ── accessors ──────────────────────────────────────────────────────────

    public String roundId() {
        return roundId;
    }

    public long seed() {
        return seed;
    }

    public LaunchConfig config() {
        return config;
    }

    public long stepMillis() {
        return stepMillis;
    }

    public WorldClock clock() {
        return clock;
    }

    /** Start the clock without the real-time driver (headless / test stepping). */
    public void startClock() {
        clock.start();
    }

    public void pauseClock() {
        clock.pause();
    }

    public void resumeClock() {
        clock.resume();
    }

    public long tick() {
        return clock.tick();
    }

    public RoundState state() {
        return lifecycle.state();
    }

    public RoundLifecycle lifecycle() {
        return lifecycle;
    }

    public InstrumentUniverse universe() {
        return universe;
    }

    public FundamentalModel fundamentals() {
        return fundamentals;
    }

    public VolatilityState volatility() {
        return volatility;
    }

    public Map<String, InstrumentEngine> engines() {
        return engines;
    }

    public SessionStateMachine session() {
        return session;
    }

    public SessionSchedule schedule() {
        return schedule;
    }

    public MarginManager margin() {
        return margin;
    }

    public TradeGateway gateway() {
        return gateway;
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

    public List<Participant> participants() {
        return participants;
    }

    public int pendingCommands() {
        return commands.size();
    }

    public boolean settled() {
        return settled;
    }
}
