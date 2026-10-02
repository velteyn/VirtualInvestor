package com.virtualinvestor.runtime;

import com.virtualinvestor.core.bot.MacroReactionStrategy;
import com.virtualinvestor.core.bot.MarketMakerStrategy;
import com.virtualinvestor.core.bot.MomentumStrategy;
import com.virtualinvestor.core.bot.NewsHunterStrategy;
import com.virtualinvestor.core.bot.ScalperStrategy;
import com.virtualinvestor.core.bot.ValueStrategy;
import com.virtualinvestor.core.clock.TimeSource;
import com.virtualinvestor.core.clock.WorldClock;
import com.virtualinvestor.core.command.Command;
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
import com.virtualinvestor.core.game.WarmupGenerator;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.news.NewsEngine;
import com.virtualinvestor.core.news.NewsGenerator;
import com.virtualinvestor.core.news.NewsItem;
import com.virtualinvestor.core.news.PlannedNews;
import com.virtualinvestor.core.participant.BotParticipant;
import com.virtualinvestor.core.participant.HumanParticipant;
import com.virtualinvestor.core.participant.Participant;
import com.virtualinvestor.core.participant.TradeGateway;
import com.virtualinvestor.core.random.RandomStreams;
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
 * It owns the clock, the fundamental/news/bot simulation, the session and margin rules, the
 * participant roster and the trade gateway, drains client commands at the top of each tick, and
 * publishes the events it produces to listeners and the durable journal.
 *
 * <p>Single-writer: {@link #step()} runs on one thread; {@link #enqueue(Command)} is the only
 * cross-thread entry.
 */
public final class RunningRound {

    private static final long STEP_MILLIS = 100L;
    private static final long YEAR_MILLIS = 5_896_800_000L;
    private static final double MARKET_ANNUAL_VOL = 0.30;
    private static final double SECTOR_ANNUAL_VOL = 0.20;
    private static final double VOLATILITY_DECAY = 0.99;
    private static final long QUOTE_REFRESH_TICKS = 50L;

    private final String roundId;
    private final long seed;
    private final LaunchConfig config;
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
    private final List<Consumer<List<GameEvent>>> listeners = new ArrayList<>();

    private WorldJournal journal = WorldJournal.noop();
    private boolean warmedUp;

    public RunningRound(String roundId, long seed, LaunchConfig config) {
        this.roundId = Objects.requireNonNull(roundId, "roundId");
        this.seed = seed;
        this.config = Objects.requireNonNull(config, "config");

        long durationTicks = config.durationTicks(STEP_MILLIS);
        this.clock = new WorldClock(STEP_MILLIS, TimeSource.system());
        this.universe = InstrumentUniverse.defaultUniverse();
        RandomStreams streams = new RandomStreams(seed);
        this.fundamentals = new FundamentalModel(
                universe, streams, MARKET_ANNUAL_VOL, SECTOR_ANNUAL_VOL, STEP_MILLIS, YEAR_MILLIS);
        this.volatility = new VolatilityState(VOLATILITY_DECAY);
        fundamentals.setVolatilityState(volatility);

        this.engines = new LinkedHashMap<>();
        this.previousPrices = new LinkedHashMap<>();
        for (Instrument instrument : universe.all()) {
            engines.put(instrument.ticker(), new InstrumentEngine(instrument.ticker(), instrument.referencePrice()));
            previousPrices.put(instrument.ticker(), instrument.referencePrice());
        }

        List<NewsItem> timeline = new NewsGenerator(universe, streams, 1500L, 0.30).generate(0L, durationTicks);
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
                new MarketMakerStrategy(0.02, 2.0, 0.001, 500L), STEP_MILLIS, 1_000_000.0);
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

        this.runners = buildRunners();
    }

    private List<BotRunner> buildRunners() {
        List<BotRunner> built = new ArrayList<>();
        MarketMakerStrategy maker = new MarketMakerStrategy(0.02, 2.0, 0.001, 500L);
        // Roster order: MARK, TIDE, LEDGER, BLITZ, VULTURE.
        built.add(new BotRunner(rivals.get(0), null, maker, QUOTE_REFRESH_TICKS));
        built.add(new BotRunner(rivals.get(1), new MomentumStrategy(0.0005, 30L), null, 0L));
        built.add(new BotRunner(rivals.get(2), new ValueStrategy(0.005, 30L), null, 0L));
        built.add(new BotRunner(rivals.get(3), new ScalperStrategy(0.0005, 20L), null, 0L));
        built.add(new BotRunner(rivals.get(4), new NewsHunterStrategy(0.01, 40L), null, 0L));
        // Institutions: ATLAS and CROWN provide liquidity; MERIDIAN trades macro news.
        built.add(new BotRunner(institutions.get(0), null, maker, QUOTE_REFRESH_TICKS));
        built.add(new BotRunner(institutions.get(1), new MacroReactionStrategy(0.01, 1_000L), null, 0L));
        built.add(new BotRunner(institutions.get(2), null, maker, QUOTE_REFRESH_TICKS));
        return List.copyOf(built);
    }

    public void setJournal(WorldJournal journal) {
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    public void addListener(Consumer<List<GameEvent>> listener) {
        listeners.add(Objects.requireNonNull(listener, "listener"));
    }

    public void enqueue(Command command) {
        commands.enqueue(command);
    }

    /** Begin the round: enter warm-up, generate history and seed books, then run. */
    public void start() {
        lifecycle.transitionTo(RoundState.WARMUP);
        if (!warmedUp) {
            long durationTicks = config.durationTicks(STEP_MILLIS);
            warmup.generateHistory(universe, fundamentals, 60L, 60L, 1_700_000_000L);
            warmup.seedBooks(engines.values(), fundamentals);
            warmedUp = true;
        }
        session.begin();
        lifecycle.transitionTo(RoundState.RUNNING);
        clock.start();
    }

    /**
     * Advance the world exactly one tick and return the events it produced. The order is fixed
     * and documented: (1) apply queued client commands, (2) advance fundamentals and
     * volatility, (3) advance the session phase, (4) release due news, (5) let the bots act,
     * (6) enforce margin.
     */
    public List<GameEvent> step() {
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
            if (transition.to() == SessionPhase.CONTINUOUS) {
                events.add(new GameEvent.SessionOpened(roundId, tick));
            } else if (transition.to() == SessionPhase.CLOSED) {
                events.add(new GameEvent.SessionClosed(roundId, tick));
            }
        }

        events.addAll(news.advance(tick));

        for (Instrument instrument : universe.all()) {
            String symbol = instrument.ticker();
            InstrumentEngine engine = engines.get(symbol);
            double fundamental = fundamentals.fundamental(symbol);
            double vol = volatility.multiplier(symbol) - 1.0;
            double change = priceChange(symbol, engine.lastPrice());
            for (BotRunner runner : runners) {
                long netPosition = runner.participant().account().position(symbol).netQty();
                events.addAll(runner.act(engine, fundamental, vol, change, netPosition, tick));
            }
        }

        Map<String, Double> marks = marks();
        for (Participant participant : participants) {
            events.addAll(margin.enforce(participant, marks, tick));
        }

        publish(events);
        return List.copyOf(events);
    }

    private List<GameEvent> applyCommand(Command command, long tick) {
        if (command instanceof Command.PlaceOrder place) {
            Participant participant = participant(place.participantId());
            InstrumentEngine engine = engines.get(place.symbol());
            return engine.submit(new com.virtualinvestor.core.book.IncomingOrder(
                    place.commandId(), place.participantId(), place.symbol(), place.side(), place.type(),
                    place.quantity(), place.price()), tick);
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
}
