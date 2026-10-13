package com.virtualinvestor.edge.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.persist.article.ArticleStore;
import org.junit.jupiter.api.Test;

class NewsroomServiceTest {

    private static GameEvent.NewsReleased release(String newsId, NewsScope scope) {
        return new GameEvent.NewsReleased(newsId, "THE_WIRE", scope, "PEAR", "Consumer Tech",
                "headline", "", 0.02, 100L,
                com.virtualinvestor.core.domain.NewsStage.CONFIRMED, 5400L);
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
}
