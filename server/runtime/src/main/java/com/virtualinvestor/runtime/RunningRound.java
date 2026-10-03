package com.virtualinvestor.runtime;

import com.virtualinvestor.core.auction.Auction;
import com.virtualinvestor.core.auction.AuctionFill;
import com.virtualinvestor.core.auction.AuctionOrder;
import com.virtualinvestor.core.auction.AuctionResult;
import com.virtualinvestor.core.book.IncomingOrder;
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

    private final String roundId;
    private final long seed;
    private final LaunchConfig config;
    private final long stepMillis;
    private final InstrumentUniverse universe;
    private final WorldClock clock;
    private final FundamentalModel fundamentals;
    private final VolatilityState volatility;
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
    private final List<BotParticipant> rivals;
    private final List<BotParticipant> institutions;
    private final List<Participant> participants;
    private final List<BotRunner> runners;
    private final Map<String, Double> previousPrices;
    private final Map<String, Double> startingCapitals;
    private final Map<String, MarketRules> marketRules = new LinkedHashMap<>();
    private final Map<String, CircuitBreaker> breakers = new LinkedHashMap<>();
    private final Map<String, ShortSalePolicy> shortPolicies = new LinkedHashMap<>();
    private final List<Consumer<List<GameEvent>>> listeners = new ArrayList<>();
    private final java.util.Set<String> processedCommands = new java.util.LinkedHashSet<>();

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
        this.roundId = Objects.requireNonNull(roundId, "roundId");
        this.seed = seed;
        this.config = Objects.requireNonNull(config, "config");
        if (stepMillis <= 0L) {
            throw new IllegalArgumentException("stepMillis must be positive");
        }
        this.stepMillis = stepMillis;

        long durationTicks = config.durationTicks(stepMillis);
        this.universe = InstrumentUniverse.defaultUniverse();
        this.clock = new WorldClock(stepMillis, TimeSource.system());
        RandomStreams streams = new RandomStreams(seed);
        this.fundamentals = new FundamentalModel(
                universe, streams, MARKET_ANNUAL_VOL, SECTOR_ANNUAL_VOL, stepMillis, YEAR_MILLIS);
        this.volatility = new VolatilityState(VOLATILITY_DECAY);
        fundamentals.setVolatilityState(volatility);

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

        this.player = new HumanParticipant("human", "You", config.startingCapital());
        this.rivals = RivalRoster.rivals(config.startingCapital());
        this.institutions = RivalRoster.institutions(config.startingCapital());
        this.participants = new ArrayList<>();
        participants.add(player);
        participants.addAll(rivals);
        participants.addAll(institutions);

        this.startingCapitals = new LinkedHashMap<>();
        for (Participant participant : participants) {
            startingCapitals.put(participant.id(), config.startingCapital());
        }

        this.runners = buildRunners();
    }

    private List<BotRunner> buildRunners() {
        List<BotRunner> built = new ArrayList<>();
        com.virtualinvestor.core.bot.MarketMakerStrategy maker =
                new com.virtualinvestor.core.bot.MarketMakerStrategy(0.02, 2.0, 0.001, 500L);
        // Roster order: MARK, TIDE, LEDGER, BLITZ, VULTURE.
        built.add(new BotRunner(rivals.get(0), null, maker, QUOTE_REFRESH_TICKS, tickFor("NVDO")));
        built.add(new BotRunner(rivals.get(1), new com.virtualinvestor.core.bot.MomentumStrategy(0.0005, 30L), null, 0L));
        built.add(new BotRunner(rivals.get(2), new com.virtualinvestor.core.bot.ValueStrategy(0.005, 30L), null, 0L));
        built.add(new BotRunner(rivals.get(3), new com.virtualinvestor.core.bot.ScalperStrategy(0.0005, 20L), null, 0L));
        built.add(new BotRunner(rivals.get(4), new com.virtualinvestor.core.bot.NewsHunterStrategy(0.01, 40L), null, 0L));
        // Institutions: ATLAS and CROWN provide liquidity; MERIDIAN trades macro news.
        built.add(new BotRunner(institutions.get(0), null, maker, QUOTE_REFRESH_TICKS, tickFor("NVDO")));
        built.add(new BotRunner(institutions.get(1),
                new com.virtualinvestor.core.bot.MacroReactionStrategy(0.01, 1_000L), null, 0L));
        built.add(new BotRunner(institutions.get(2), null, maker, QUOTE_REFRESH_TICKS, tickFor("NVDO")));
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
            warmup.seedBooks(engines.values(), fundamentals, rivals.get(0).id());
            warmedUp = true;
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

        for (SessionTransition transition : session.advance(tick)) {
            handleTransition(transition, tick, events);
        }

        events.addAll(news.advance(tick));

        if (session.phase() == SessionPhase.CONTINUOUS) {
            for (Instrument instrument : universe.all()) {
                String symbol = instrument.ticker();
                InstrumentEngine engine = engines.get(symbol);
                double fundamental = fundamentals.fundamental(symbol);
                double vol = volatility.multiplier(symbol) - 1.0;
                double change = priceChange(symbol, engine.lastPrice());
                for (BotRunner runner : runners) {
                    long netPosition = runner.participant().account().position(symbol).netQty();
                    events.addAll(runner.act(engine, fundamental, vol, change, netPosition, tick,
                            fill -> applyFill(fill, symbol)));
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

        publish(events);
        return List.copyOf(events);
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
                    participant(fill.participantId()).account()
                            .applyFill(symbol, fill.side(), fill.quantity(), fill.price());
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

    /** Credit both counterparties of a fill before the event is published. */
    private void applyFill(com.virtualinvestor.core.book.Fill fill, String symbol) {
        participant(fill.takerParticipantId()).account()
                .applyFill(symbol, fill.takerSide(), fill.quantity(), fill.price());
        participant(fill.makerParticipantId()).account()
                .applyFill(symbol, fill.makerSide(), fill.quantity(), fill.price());
    }

    private static List<GameEvent> rejected(Command.PlaceOrder place, String reason, long tick) {
        return List.of(
                new GameEvent.OrderAccepted(place.commandId(), place.participantId(), place.symbol(), tick),
                new GameEvent.OrderRejected(place.commandId(), place.participantId(), place.symbol(), reason, tick));
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
                    return List.of(new GameEvent.OrderAccepted(
                            place.commandId(), place.participantId(), place.symbol(), tick));
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
            for (com.virtualinvestor.core.book.Fill fill : result.fills()) {
                applyFill(fill, place.symbol());
            }
            return result.events();
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

    private Participant participant(String id) {
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
