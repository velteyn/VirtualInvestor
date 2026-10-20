package com.virtualinvestor.core.news;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.domain.NewsStage;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.random.RandomStreams;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewsGeneratorTest {

    private static final InstrumentUniverse UNIVERSE = InstrumentUniverse.defaultUniverse();

    private static List<NewsItem> generate(long seed, long sessionTicks) {
        return new NewsGenerator(UNIVERSE, new RandomStreams(seed), 1500L, 0.30).generate(1000L, sessionTicks);
    }

    @Test
    void sameSeedProducesSameEvents() {
        assertEquals(generate(42L, 18_000L), generate(42L, 18_000L));
    }

    @Test
    void differentSeedsDiffer() {
        assertNotEquals(generate(1L, 18_000L), generate(2L, 18_000L));
    }

    @Test
    void eventCountScalesWithSessionLength() {
        int shortSession = generate(7L, 18_000L).size();
        int longSession = generate(7L, 72_000L).size();
        assertTrue(longSession > shortSession, () -> shortSession + " vs " + longSession);
    }

    @Test
    void validityScalesWithScopeAndMagnitude() {
        // 180-tick session: a 2% earnings burns out in ~18 ticks, a 3% macro
        // shock stays tradeable ~81, tiny noise floors at barely reactable.
        assertEquals(18L, NewsGenerator.validityTicks(180L, NewsScope.COMPANY, 0.02));
        assertEquals(81L, NewsGenerator.validityTicks(180L, NewsScope.MACRO, 0.03));
        assertEquals(5L, NewsGenerator.validityTicks(180L, NewsScope.COMPANY, 0.001));
        assertEquals(180L, NewsGenerator.validityTicks(180L, NewsScope.MACRO, 1.0));
    }

    @Test
    void eventsAreSortedByTick() {
        List<NewsItem> items = generate(5L, 18_000L);
        for (int i = 1; i < items.size(); i++) {
            assertTrue(items.get(i - 1).tick() <= items.get(i).tick());
        }
    }

    @Test
    void eventsIncludeMultipleScopes() {
        boolean company = false;
        boolean sector = false;
        boolean macro = false;
        for (NewsItem item : generate(11L, 72_000L)) {
            company |= item.scope() == NewsScope.COMPANY;
            sector |= item.scope() == NewsScope.SECTOR;
            macro |= item.scope() == NewsScope.MACRO;
        }
        assertTrue(company && sector && macro, "expected company, sector and macro news");
    }

    @Test
    void tiersDealRoughlySixtyThirtyTen() {
        List<NewsItem> items = generate(23L, 360_000L);
        assertTrue(items.size() > 200, "need a crowd to split, had " + items.size());
        long wire = items.stream().filter(i -> i.tier() == ReliabilityTier.WIRE).count();
        long patron = items.stream().filter(i -> i.tier() == ReliabilityTier.PATRON).count();
        long spy = items.stream().filter(i -> i.tier() == ReliabilityTier.SPY).count();
        double total = items.size();
        assertTrue(Math.abs(wire / total - 0.6) < 0.08, "wire ~60%, was " + wire / total);
        assertTrue(Math.abs(patron / total - 0.3) < 0.08, "patron ~30%, was " + patron / total);
        assertTrue(Math.abs(spy / total - 0.1) < 0.08, "spy ~10%, was " + spy / total);
    }

    @Test
    void sameSeedDealsSameTiers() {
        List<NewsItem> first = generate(42L, 36_000L);
        List<NewsItem> second = generate(42L, 36_000L);
        assertEquals(first.size(), second.size());
        for (int i = 0; i < first.size(); i++) {
            assertEquals(first.get(i).tier(), second.get(i).tier(), "tier at index " + i);
        }
    }

    @Test
    void spyRumorsDenyMoreOftenThanWire() {
        // Rumor-stage items only: scheduled earnings carry the CONFIRMED stage already.
        long[] confirmed = new long[ReliabilityTier.values().length];
        long[] total = new long[ReliabilityTier.values().length];
        for (long seed = 100L; seed < 140L; seed++) {
            for (NewsItem item : generate(seed, 72_000L)) {
                if (item.stage() != NewsStage.RUMOR) {
                    continue;
                }
                total[item.tier().ordinal()]++;
                if (item.confirmed()) {
                    confirmed[item.tier().ordinal()]++;
                }
            }
        }
        double wire = confirmed[ReliabilityTier.WIRE.ordinal()] / (double) total[ReliabilityTier.WIRE.ordinal()];
        double patron = confirmed[ReliabilityTier.PATRON.ordinal()] / (double) total[ReliabilityTier.PATRON.ordinal()];
        double spy = confirmed[ReliabilityTier.SPY.ordinal()] / (double) total[ReliabilityTier.SPY.ordinal()];
        assertTrue(wire > patron, "wire confirms more than patron: " + wire + " vs " + patron);
        assertTrue(patron > spy, "patron confirms more than spy: " + patron + " vs " + spy);
    }

    @Test
    void stagedStoriesCarryDistinctLedes() {
        List<NewsItem> items = generate(42L, 72_000L);
        boolean randomTriple = false;
        boolean scenarioTriple = false;
        boolean earningsSingle = false;
        for (NewsItem item : items) {
            if (item.id().startsWith("rand-") && item.stage() == NewsStage.RUMOR) {
                assertTrue(item.confirmHeadline() != null && !item.confirmHeadline().isBlank());
                assertTrue(item.denyHeadline() != null && !item.denyHeadline().isBlank());
                assertTrue(!item.confirmHeadline().equals(item.headline()),
                        "confirm lede must differ from the rumor: " + item.confirmHeadline());
                assertTrue(!item.denyHeadline().equals(item.headline()));
                randomTriple = true;
            }
            if (item.id().startsWith("scen-")) {
                assertTrue(item.confirmHeadline() != null && !item.confirmHeadline().isBlank());
                assertTrue(item.denyHeadline() != null && !item.denyHeadline().isBlank());
                assertTrue(!item.confirmHeadline().equals(item.headline()));
                scenarioTriple = true;
            }
            if (item.id().startsWith("sched-")) {
                assertTrue(item.confirmHeadline() == null && item.denyHeadline() == null,
                        "earnings keep the single headline");
                assertTrue(item.body() != null && !item.body().isBlank(), "earnings carry a pack body");
                earningsSingle = true;
            }
        }
        assertTrue(randomTriple && scenarioTriple && earningsSingle, "expected all three story shapes");
    }
}
