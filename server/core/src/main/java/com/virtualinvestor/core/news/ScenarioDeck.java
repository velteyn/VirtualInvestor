package com.virtualinvestor.core.news;

import com.virtualinvestor.core.domain.NewsScope;
import java.util.List;

/**
 * The rare-event deck (market-scenarios): takeover bids, scandals, crashes,
 * squeezes, macro shocks, meme stampedes and fraud probes. Each entry carries
 * its magnitude band, rumor staging and confirm bias; headlines and bodies
 * take {@code {name}} / {@code {sector}} slots filled from the picked equity.
 * All names come from the universe, so the fictional-names guard in
 * {@link NewsItem} holds by construction.
 */
final class ScenarioDeck {

    record Entry(
            String key,
            NewsSource source,
            NewsScope scope,
            double minMagnitude,
            double maxMagnitude,
            /** 0.0 means either direction (drawn); else fixed +1 or -1. */
            double fixedDirection,
            double rumorFraction,
            double minDelayFraction,
            double maxDelayFraction,
            double confirmBias,
            String headline,
            String body) {
    }

    static final List<Entry> ENTRIES = List.of(
            new Entry("takeover", NewsSource.THE_WIRE, NewsScope.COMPANY,
                    0.28, 0.35, 1.0, 0.5, 0.05, 0.08, 0.65,
                    "{name} receives takeover approach at a fat premium",
                    "Takeover approach reported for {name}."),
            new Entry("scandal", NewsSource.MERIDIAN_NEWS, NewsScope.COMPANY,
                    0.22, 0.28, -1.0, 0.6, 0.03, 0.06, 0.70,
                    "Auditors refuse to sign off on {name} books",
                    "Accounting questions mount at {name}."),
            new Entry("crash", NewsSource.TAPE, NewsScope.MACRO,
                    0.10, 0.12, -1.0, 0.7, 0.02, 0.04, 0.25,
                    "Flash crash sweeps the tape",
                    "Sudden air pocket hits the market."),
            new Entry("squeeze", NewsSource.TAPE, NewsScope.COMPANY,
                    0.18, 0.22, 1.0, 0.8, 0.03, 0.06, 1.0,
                    "Shorts scramble to cover {name}",
                    "Short squeeze erupts in {name}."),
            new Entry("bank", NewsSource.THE_WIRE, NewsScope.MACRO,
                    0.09, 0.12, 0.0, 0.5, 0.05, 0.08, 0.80,
                    "Central bank shocks the market",
                    "Surprise policy move reprices everything."),
            new Entry("meme", NewsSource.TAPE, NewsScope.COMPANY,
                    0.13, 0.17, 1.0, 0.9, 0.03, 0.06, 1.0,
                    "Stampede piles into {name}",
                    "Meme frenzy lifts {name}."),
            new Entry("probe", NewsSource.MERIDIAN_NEWS, NewsScope.COMPANY,
                    0.15, 0.20, -1.0, 0.6, 0.03, 0.06, 0.70,
                    "Fraud probe opens on {name}, chief ousted",
                    "Regulators probe {name}; chief executive out."));

    private ScenarioDeck() {
    }
}
