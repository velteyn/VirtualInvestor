package com.virtualinvestor.core.news;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.NewsStage;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.fundamental.FundamentalModel;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.random.RandomStreams;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Market-scenarios: deck shape, pacing, determinism and staged resolution. */
class ScenarioDeckTest {

    private static final long SESSION_TICKS = 18_000L;

    private static List<Instrument> equities() {
        return InstrumentUniverse.defaultUniverse().equities();
    }

    private static List<NewsItem> timeline(long seed) {
        return new NewsGenerator(InstrumentUniverse.defaultUniverse(),
                new RandomStreams(seed), 1500L, 0.30).generate(0L, SESSION_TICKS);
    }

    private static List<NewsItem> scenarios(List<NewsItem> timeline) {
        return timeline.stream().filter(item -> item.id().startsWith("scen-")).toList();
    }

    @Test
    void deckHasSevenValidEntries() {
        assertEquals(7, ScenarioDeck.ENTRIES.size());
        for (ScenarioDeck.Entry entry : ScenarioDeck.ENTRIES) {
            assertTrue(entry.minMagnitude() > 0.0 && entry.maxMagnitude() >= entry.minMagnitude(),
                    entry.key() + " needs a positive magnitude band");
            assertTrue(entry.rumorFraction() > 0.0 && entry.rumorFraction() <= 1.0,
                    entry.key() + " needs a valid rumor fraction");
            assertTrue(entry.confirmBias() >= 0.0 && entry.confirmBias() <= 1.0,
                    entry.key() + " needs a valid confirm bias");
            assertTrue(!entry.headline().contains("{name}") || entry.scope() != null,
                    entry.key() + " headlines resolve against a picked equity");
        }
    }

    @Test
    void bigShocksReachBreakerBands() {
        for (String key : List.of("takeover", "scandal", "squeeze", "probe", "bank", "crash", "meme")) {
            ScenarioDeck.Entry entry = ScenarioDeck.ENTRIES.stream()
                    .filter(e -> e.key().equals(key)).findFirst().orElseThrow();
            assertTrue(entry.maxMagnitude() >= 0.08,
                    key + " should move markets violently, max is " + entry.maxMagnitude());
        }
    }

    @Test
    void pacingHoldsAcrossSeeds() {
        for (long seed = 1L; seed <= 10L; seed++) {
            List<NewsItem> scen = scenarios(timeline(seed));
            assertTrue(scen.size() >= 1 && scen.size() <= 3,
                    "seed " + seed + " planned " + scen.size());
            List<Long> ticks = scen.stream().map(NewsItem::tick).sorted().toList();
            for (int i = 0; i < ticks.size(); i++) {
                assertTrue(ticks.get(i) >= SESSION_TICKS / 10L,
                        "seed " + seed + " scenario inside the opening 10%");
                if (i > 0) {
                    assertTrue(ticks.get(i) - ticks.get(i - 1) >= (long) (SESSION_TICKS * 0.15),
                            "seed " + seed + " scenarios too close");
                }
                NewsItem item = scen.get(i);
                assertTrue(item.tick() + item.resolutionDelayTicks() <= SESSION_TICKS,
                        "seed " + seed + " resolution past the close");
            }
        }
    }

    @Test
    void sameSeedSameDeck() {
        assertEquals(timeline(42L), timeline(42L));
    }

    @Test
    void rumorResolvesThroughTheEngine() {
        List<NewsItem> scen = scenarios(timeline(7L));
        assertTrue(!scen.isEmpty(), "seed 7 should plan scenarios");
        FundamentalModel model = new FundamentalModel(
                InstrumentUniverse.defaultUniverse(), new RandomStreams(1L), 0.30, 0.20, 100L, 100_000L);
        List<PlannedNews> planned = new ArrayList<>();
        for (NewsItem item : scen) {
            planned.add(new PlannedNews(item, item.tick() + item.resolutionDelayTicks(),
                    item.confirmed(), item.rumorFraction()));
        }
        NewsEngine engine = new NewsEngine(model, planned);
        List<GameEvent> released = new ArrayList<>();
        List<GameEvent> resolved = new ArrayList<>();
        for (long tick = 0L; tick <= SESSION_TICKS; tick++) {
            for (GameEvent event : engine.advance(tick)) {
                if (event instanceof GameEvent.NewsReleased) {
                    released.add(event);
                } else if (event instanceof GameEvent.NewsConfirmed
                        || event instanceof GameEvent.NewsDenied) {
                    resolved.add(event);
                }
            }
        }
        assertEquals(scen.size(), released.size(), "every scenario rumor releases");
        assertEquals(scen.size(), resolved.size(), "every scenario resolves");
    }

    @Test
    void scenarioBodiesStayFictional() {
        for (NewsItem item : scenarios(timeline(99L))) {
            assertEquals(NewsStage.RUMOR, item.stage());
        }
    }
}
