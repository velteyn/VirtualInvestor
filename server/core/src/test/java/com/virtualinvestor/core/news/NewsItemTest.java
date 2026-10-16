package com.virtualinvestor.core.news;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.domain.NewsStage;
import org.junit.jupiter.api.Test;

class NewsItemTest {

    private static NewsItem item(NewsSource source, NewsScope scope, String symbol, String sector,
                                 String headline, String body, double direction, double magnitude) {
        return new NewsItem("n1", source, scope, symbol, sector, headline, body,
                direction, magnitude, 300L, NewsStage.RUMOR, 10L, 0.5, 100L, true);
    }

    @Test
    void validFictionalItemIsAccepted() {
        NewsItem news = item(NewsSource.THE_WIRE, NewsScope.COMPANY, "NVDO", null,
                "Nvido lifts guidance", "Chip demand stays strong", 1.0, 0.05);

        assertEquals(0.05, news.signedMagnitude(), 1e-9);
        assertEquals(NewsSource.THE_WIRE, news.source());
    }

    @Test
    void realNewsSourceRejected() {
        assertThrows(IllegalArgumentException.class, () -> NewsSource.fromToken("Reuters"));
        assertThrows(IllegalArgumentException.class, () -> NewsSource.fromToken("Bloomberg"));
        assertThrows(IllegalArgumentException.class, () -> NewsSource.fromToken("made-up"));
    }

    @Test
    void realCompanyNameInHeadlineRejected() {
        assertThrows(IllegalArgumentException.class, () -> item(NewsSource.TAPE, NewsScope.COMPANY, "PEAR", null,
                "Apple beats forecasts", "body", 1.0, 0.02));
    }

    @Test
    void companyScopeRequiresSymbol() {
        assertThrows(IllegalArgumentException.class, () -> item(NewsSource.TAPE, NewsScope.COMPANY, null, null,
                "Something happened", "body", 1.0, 0.02));
    }

    @Test
    void sectorScopeRequiresSector() {
        assertThrows(IllegalArgumentException.class, () -> item(NewsSource.TAPE, NewsScope.SECTOR, null, null,
                "Sector wobble", "body", -1.0, 0.01));
    }

    @Test
    void directionAndMagnitudeValidated() {
        assertThrows(IllegalArgumentException.class, () -> item(NewsSource.TAPE, NewsScope.MACRO, null, null,
                "Macro", "body", 2.0, 0.01));
        assertThrows(IllegalArgumentException.class, () -> item(NewsSource.TAPE, NewsScope.MACRO, null, null,
                "Macro", "body", 1.0, -0.01));
    }

    @Test
    void metaverseIsNotMistakenForMeta() {
        // Token-based detection must not flag a word that merely contains a real name.
        assertFalse(FictionalNames.containsRealName("The metaverse keeps growing"));
        assertTrue(FictionalNames.containsRealName("Meta Platforms rallies"));
    }
}
