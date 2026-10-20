package com.virtualinvestor.edge.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.persist.article.ArticleStore;
import org.junit.jupiter.api.Test;

class NewsroomServiceTest {

    private static GameEvent.NewsReleased release(String newsId, NewsScope scope) {
        return new GameEvent.NewsReleased(newsId, "THE_WIRE", scope, "PEAR", "Consumer Tech",
                "headline", "", 0.02, 100L,
                com.virtualinvestor.core.domain.NewsStage.CONFIRMED, 5400L,
                com.virtualinvestor.core.news.ReliabilityTier.WIRE);
    }

    @Test
    void earningsSectorMacroEligible() {
        assertEquals(ArticleStore.Kind.EARNINGS, NewsroomService.kindOf(release("sched-PEAR", NewsScope.COMPANY)));
        assertEquals(ArticleStore.Kind.SECTOR, NewsroomService.kindOf(release("rand-1", NewsScope.SECTOR)));
        assertEquals(ArticleStore.Kind.MACRO, NewsroomService.kindOf(release("rand-2", NewsScope.MACRO)));
    }

    @Test
    void randomCompanyNoiseSkipped() {
        assertNull(NewsroomService.kindOf(release("rand-9", NewsScope.COMPANY)));
    }

    @Test
    void scenarioBeatsAreEligible() {
        GameEvent.NewsReleased beat = new GameEvent.NewsReleased("scen-takeover-0-b1", "THE_WIRE",
                NewsScope.COMPANY, "NVDO", "Tech", "headline", "", 0.15, 1000L,
                com.virtualinvestor.core.domain.NewsStage.RUMOR, 3000L,
                com.virtualinvestor.core.news.ReliabilityTier.WIRE);
        assertEquals(ArticleStore.Kind.SCENARIO, NewsroomService.kindOf(beat));
    }

    @Test
    void tauntsNeverReachTheNewsroom() {
        GameEvent.NewsReleased banter = new GameEvent.NewsReleased("taunt-0", "TAPE",
                NewsScope.MACRO, null, null, "Half the room is green.", "", 0.0, 5L,
                com.virtualinvestor.core.domain.NewsStage.RUMOR, 100L,
                com.virtualinvestor.core.news.ReliabilityTier.WIRE);
        assertNull(NewsroomService.kindOf(banter));
    }

    @Test
    void stalenessNeedsAClock() {
        // Unknown tick (old rounds): never shed, narrate as today.
        assertEquals(false, NewsroomService.isStale(5400L, 100L, null));
    }

    @Test
    void freshReleaseSurvives() {
        assertEquals(false, NewsroomService.isStale(5400L, 100L, 5450L));
    }

    @Test
    void expiredReleaseSheds() {
        assertEquals(true, NewsroomService.isStale(5400L, 100L, 5600L));
    }

    @Test
    void boundaryReleaseSurvives() {
        assertEquals(false, NewsroomService.isStale(5400L, 100L, 5500L));
    }

    @Test
    void lanesPreferArcs() {
        assertEquals(0, NewsroomService.laneOf("scen-takeover-0-b1"));
        assertEquals(1, NewsroomService.laneOf("sched-PEAR"));
        assertEquals(1, NewsroomService.laneOf("preview-r1"));
        assertEquals(1, NewsroomService.laneOf(null));
    }

    @Test
    void arcsNeverShed() {
        assertEquals(false, NewsroomService.shedFor("scen-takeover-0-b1", 5400L, 100L, 99999L));
        assertEquals(true, NewsroomService.shedFor("sched-PEAR", 5400L, 100L, 99999L));
        assertEquals(false, NewsroomService.shedFor("sched-PEAR", 5400L, 100L, 5450L));
    }

    @Test
    void movesBecomeWordsWithoutFigures() {
        assertEquals("little changed", NewsroomService.wordsFor(0.2));
        assertEquals("up about four percent", NewsroomService.wordsFor(4.2));
        assertEquals("down just over twelve percent", NewsroomService.wordsFor(-11.6));
        assertEquals("up about twenty-one percent", NewsroomService.wordsFor(21.0));
        for (double move : new double[] {0.2, 4.2, -11.6, 21.0, 150.0, -0.4}) {
            String words = NewsroomService.wordsFor(move);
            assertTrue(words.chars().noneMatch(Character::isDigit),
                    "no digits allowed near the denylist: " + words);
            assertTrue(!words.contains("%"), "no percent sign: " + words);
        }
    }

    @Test
    void deskOrdersArcsFirstThenFifo() {        NewsroomService.DeskTask arcLate =
                new NewsroomService.DeskTask(9L, NewsroomService.laneOf("scen-crash-0-b0"), () -> {
                });
        NewsroomService.DeskTask routineEarly =
                new NewsroomService.DeskTask(1L, NewsroomService.laneOf("sched-PEAR"), () -> {
                });
        NewsroomService.DeskTask routineLater =
                new NewsroomService.DeskTask(2L, NewsroomService.laneOf("sched-BZAR"), () -> {
                });
        assertTrue(arcLate.compareTo(routineEarly) < 0, "arc jumps the queue");
        assertTrue(routineEarly.compareTo(routineLater) < 0, "routine stays FIFO");
        assertTrue(routineLater.compareTo(routineEarly) > 0, "FIFO is antisymmetric");
    }
}
