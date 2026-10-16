package com.virtualinvestor.core.e2e;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.bot.BotContext;
import com.virtualinvestor.core.bot.BotStrategy;
import com.virtualinvestor.core.bot.MarketMakerStrategy;
import com.virtualinvestor.core.bot.MomentumStrategy;
import com.virtualinvestor.core.bot.NewsHunterStrategy;
import com.virtualinvestor.core.bot.OrderIntent;
import com.virtualinvestor.core.bot.ScalperStrategy;
import com.virtualinvestor.core.bot.ValueStrategy;
import com.virtualinvestor.core.book.IncomingOrder;
import com.virtualinvestor.core.book.RestingOrder;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.RoundState;
import com.virtualinvestor.core.domain.SessionPhase;
import com.virtualinvestor.core.engine.InstrumentEngine;
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
import com.virtualinvestor.core.news.NewsEngine;
import com.virtualinvestor.core.news.NewsGenerator;
import com.virtualinvestor.core.news.NewsItem;
import com.virtualinvestor.core.news.PlannedNews;
import com.virtualinvestor.core.participant.BotParticipant;
import com.virtualinvestor.core.participant.Standing;
import com.virtualinvestor.core.rules.RealismPreset;
import com.virtualinvestor.core.session.SessionSchedule;
import com.virtualinvestor.core.session.SessionStateMachine;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * End-to-end bot-only round (task 12.1): warm up history and books, run a compressed
 * session driven by the fundamental model, the news timeline and the five rival bots, and
 * confirm prices move, books update, news fires, and settlement ranks everyone.
 */
class BotOnlyRoundTest {

    private static final long STEP_MILLIS = 100L;
    private static final long YEAR_MILLIS = 100_000L;
    private static final int SESSION_TICKS = 3_000;

    @Test
    void botOnlyRoundRunsAndSettles() {
        long seed = 20261002L;
        double capital = 100_000.0;
        LaunchConfig config = new LaunchConfig(capital, 30L, RealismPreset.REALISTIC);
        RoundLifecycle lifecycle = new RoundLifecycle("round-e2e", seed, config);
        lifecycle.transitionTo(RoundState.WARMUP);

        InstrumentUniverse universe = InstrumentUniverse.defaultUniverse();
        Map<String, InstrumentEngine> engines = new LinkedHashMap<>();
        for (Instrument instrument : universe.all()) {
            engines.put(instrument.ticker(), new InstrumentEngine(instrument.ticker(), instrument.referencePrice()));
        }

        FundamentalModel model = new FundamentalModel(
                universe, new com.virtualinvestor.core.random.RandomStreams(seed), 0.30, 0.20, STEP_MILLIS, YEAR_MILLIS);
        VolatilityState volatility = new VolatilityState(0.99);
        model.setVolatilityState(volatility);

        // Warm up: history and a seeded book.
        MarketMakerStrategy maker = new MarketMakerStrategy(0.02, 2.0, 0.001, 500L);
        WarmupGenerator warmup = new WarmupGenerator(maker, STEP_MILLIS, 1_000_000.0);
        warmup.generateHistory(universe, model, 60L, 60L, 1_700_000_000L);
        warmup.seedBooks(engines.values(), model);

        // News timeline + engine.
        List<NewsItem> generated = new NewsGenerator(
                universe, new com.virtualinvestor.core.random.RandomStreams(seed), 300L, 0.2)
                .generate(0L, SESSION_TICKS);
        List<PlannedNews> planned = new ArrayList<>();
        for (NewsItem item : generated) {
            planned.add(new PlannedNews(item, item.tick() + item.resolutionDelayTicks(),
                    item.confirmed(), item.rumorFraction()));
        }
        NewsEngine news = new NewsEngine(model, planned);
        news.setVolatilityState(volatility);

        // Rivals with distinct strategies, in roster order: MARK, TIDE, LEDGER, BLITZ, VULTURE.
        List<BotParticipant> rivals = RivalRoster.rivals(capital);
        List<BotStrategy> rivalStrategies = List.of(
                new MomentumStrategy(0.0005, 30L),
                new ValueStrategy(0.005, 30L),
                new ScalperStrategy(0.0005, 20L),
                new NewsHunterStrategy(0.01, 40L));
        // MARK (index 0) is a market maker: it quotes rather than takes, so it has no
        // taker strategy here and is excluded from the taker loop.
        BotStrategy[] runners = new BotStrategy[rivals.size()];
        for (int i = 1; i < rivals.size(); i++) {
            runners[i] = rivalStrategies.get((i - 1) % rivalStrategies.size());
        }

        SessionSchedule schedule = SessionSchedule.standard(SESSION_TICKS);
        SessionStateMachine session = new SessionStateMachine(schedule, 0L);
        lifecycle.transitionTo(RoundState.RUNNING);

        MarginManager margin = new MarginManager(config.preset().maintenanceMarginFraction());
        String focus = "NVDO";
        double firstPrice = engines.get(focus).lastPrice();
        double previousPrice = firstPrice;
        int trades = 0;
        int newsApplied = 0;

        for (long tick = 0L; tick <= SESSION_TICKS; tick++) {
            model.step();
            volatility.step();
            session.advance(tick);
            newsApplied += news.advance(tick).size();
            if (session.phaseAt(tick) != SessionPhase.CONTINUOUS) {
                continue;
            }

            InstrumentEngine engine = engines.get(focus);
            double fundamental = model.fundamental(focus);
            double change = previousPrice > 0 ? (engine.lastPrice() - previousPrice) / previousPrice : 0.0;
            previousPrice = engine.lastPrice();

            for (int i = 0; i < rivals.size(); i++) {
                if (runners[i] == null) {
                    continue;
                }
                BotContext context = new BotContext(focus, engine.lastPrice(), fundamental, change,
                        volatility.multiplier(focus) - 1.0, rivals.get(i).account().position(focus).netQty(),
                        rivals.get(i).account().cash(), null, tick);
                for (OrderIntent intent : runners[i].decide(context)) {

                    List<com.virtualinvestor.core.event.GameEvent> events = engine.submit(new IncomingOrder(
                            "bot-" + i + "-" + tick, rivals.get(i).id(), focus, intent.side(), intent.type(),
                            intent.quantity(), intent.price()), tick);
                    trades += (int) events.stream().filter(e -> e instanceof com.virtualinvestor.core.event.GameEvent.TradePrinted).count();
                }
            }
        }

        assertTrue(trades > 0, "rivals should trade during the session");
        assertTrue(engines.get(focus).lastPrice() != firstPrice, "price should move");
        assertTrue(newsApplied > 0, "news should fire");

        // Settlement: mark to close, force-flat, rank.
        Map<String, Double> closing = new LinkedHashMap<>();
        for (Map.Entry<String, InstrumentEngine> entry : engines.entrySet()) {
            closing.put(entry.getKey(), entry.getValue().lastPrice());
        }
        Map<String, Double> capitals = new LinkedHashMap<>();
        capitals.put("human", capital);
        for (BotParticipant rival : rivals) {
            capitals.put(rival.id(), capital);
        }
        lifecycle.transitionTo(RoundState.CLOSING);
        List<com.virtualinvestor.core.participant.Participant> everyone = new ArrayList<>(rivals);
        Settlement.Result result = Settlement.settle(everyone, closing, capitals, SESSION_TICKS, "round-e2e");

        assertEquals(5, result.standings().size(), "five rivals ranked");
        for (Standing standing : result.standings()) {
            assertTrue(Double.isFinite(standing.equity()));
        }
        lifecycle.transitionTo(RoundState.SETTLED);
        assertEquals(RoundState.SETTLED, lifecycle.state());
    }
}
