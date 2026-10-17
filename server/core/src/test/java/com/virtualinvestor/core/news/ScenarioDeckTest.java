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

    private static java.util.Map<String, List<NewsItem>> arcs(List<NewsItem> timeline) {
        java.util.Map<String, List<NewsItem>> grouped = new java.util.TreeMap<>();
        for (NewsItem item : scenarios(timeline)) {
            grouped.computeIfAbsent(StoryIds.storyOf(item.id()), key -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    @Test
    void deckHasSevenValidEntries() {
        assertEquals(7, ScenarioDeck.ENTRIES.size());
        for (ScenarioDeck.Entry entry : ScenarioDeck.ENTRIES) {
            assertTrue(entry.minMagnitude() > 0.0 && entry.maxMagnitude() >= entry.minMagnitude(),
                    entry.key() + " needs a positive magnitude band");
            assertTrue(!entry.beats().isEmpty(), entry.key() + " needs at least one beat");
            for (ScenarioDeck.Beat beat : entry.beats()) {
                assertTrue(beat.rumorFraction() > 0.0 && beat.rumorFraction() <= 1.0,
                        entry.key() + " needs a valid rumor fraction");
                assertTrue(beat.confirmBias() >= 0.0 && beat.confirmBias() <= 1.0,
                        entry.key() + " needs a valid confirm bias");
                assertTrue(beat.magnitudeShare() > 0.0,
                        entry.key() + " beats need a positive share");
            }
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
            var stories = arcs(timeline(seed));
            assertTrue(stories.size() >= 1 && stories.size() <= 3,
                    "seed " + seed + " planned " + stories.size());
            List<Long> starts = stories.values().stream()
                    .map(beats -> beats.stream().mapToLong(NewsItem::tick).min().orElseThrow())
                    .sorted().toList();
            for (int i = 0; i < starts.size(); i++) {
                assertTrue(starts.get(i) >= SESSION_TICKS / 10L,
                        "seed " + seed + " arc inside the opening 10%");
                if (i > 0) {
                    assertTrue(starts.get(i) - starts.get(i - 1) >= (long) (SESSION_TICKS * 0.15),
                            "seed " + seed + " arcs too close");
                }
            }
            for (var entry : stories.entrySet()) {
                List<NewsItem> beats = entry.getValue().stream()
                        .sorted(java.util.Comparator.comparingLong(NewsItem::tick)).toList();
                for (int b = 1; b < beats.size(); b++) {
                    assertTrue(beats.get(b).tick() >= beats.get(b - 1).tick(),
                            "seed " + seed + " beats out of order in " + entry.getKey());
                }
                for (NewsItem item : beats) {
                    assertTrue(item.tick() + item.resolutionDelayTicks() <= SESSION_TICKS,
                            "seed " + seed + " resolution past the close");
                    assertTrue(!item.headline().contains("{") && !item.body().contains("{"),
                            "seed " + seed + " unresolved placeholder in " + item.id());
                }
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

    @Test
    void bibleLookup() {
        assertTrue(ScenarioDeck.bibleForStory("scen-takeover-0").contains("Halcyon Capital"));
        assertTrue(!ScenarioDeck.bibleForStory("scen-crash-0").isEmpty());
        assertEquals("", ScenarioDeck.bibleForStory("sched-PEAR"));
        assertEquals("", ScenarioDeck.bibleForStory(null));
    }

    @Test
    void biblesStayDenylistSafe() {
        for (ScenarioDeck.Entry entry : ScenarioDeck.ENTRIES) {
            String bible = entry.bible();
            assertTrue(!bible.isEmpty(), entry.key() + " has no bible");
            assertTrue(!FictionalNames.containsRealName(bible),
                    entry.key() + " bible names a real company: " + bible);
            assertTrue(bible.chars().noneMatch(Character::isDigit),
                    entry.key() + " bible carries digits the denylist would decline: " + bible);
            assertTrue(!bible.contains("%") && !bible.contains("$"),
                    entry.key() + " bible carries a figure mark: " + bible);
        }
    }

    @Test
    void beatSharesSumToWhole() {
        for (ScenarioDeck.Entry entry : ScenarioDeck.ENTRIES) {
            double shares = entry.beats().stream().mapToDouble(ScenarioDeck.Beat::magnitudeShare).sum();
            assertEquals(1.0, shares, 1e-9, entry.key() + " beats must sum to the arc total");
        }
    }

    @Test
    void storyIdsGroupBeats() {
        assertTrue(StoryIds.isBeat("scen-takeover-0-b2"));
        assertEquals("scen-takeover-0", StoryIds.storyOf("scen-takeover-0-b2"));
        assertEquals("scen-takeover-0-b0", StoryIds.beatId("scen-takeover-0", 0));
        for (long seed = 1L; seed <= 10L; seed++) {
            for (var entry : arcs(timeline(seed)).entrySet()) {
                List<NewsItem> beats = entry.getValue();
                assertTrue(!beats.isEmpty(), "arc " + entry.getKey() + " is empty");
                assertTrue(beats.stream().allMatch(b -> StoryIds.storyOf(b.id()).equals(entry.getKey())));
            }
        }
    }
}
