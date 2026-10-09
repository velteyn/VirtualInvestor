package com.virtualinvestor.core.news;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.NewsScope;
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
}
