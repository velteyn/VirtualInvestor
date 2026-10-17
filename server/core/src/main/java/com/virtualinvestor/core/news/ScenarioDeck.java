package com.virtualinvestor.core.news;

import com.virtualinvestor.core.domain.NewsScope;
import java.util.List;

/**
 * The rare-event deck (market-scenarios): takeover bids, scandals, crashes,
 * squeezes, macro shocks, meme stampedes and fraud probes. Each entry carries
 * its magnitude band plus a beat plan; headlines and bodies take
 * {@code {name}} / {@code {sector}} slots filled from the picked equity. All
 * names come from the universe, so the fictional-names guard in
 * {@link NewsItem} holds by construction.
 *
 * <p>Arcs (narrator-arcs): entries with several beats play multi-act stories
 * whose per-beat magnitudes sum to the arc total. The {@code bible} holds
 * canon facts the narrator may use (named facilities, people, worded-out
 * figures — never digits or % signs, which the denylist would decline).
 */
public final class ScenarioDeck {

    /** One act of an arc: its share of the total magnitude plus staging. */
    record Beat(
            String headline,
            String body,
            double magnitudeShare,
            double rumorFraction,
            double minDelayFraction,
            double maxDelayFraction,
            double confirmBias) {
    }

    record Entry(
            String key,
            NewsSource source,
            NewsScope scope,
            double minMagnitude,
            double maxMagnitude,
            /** 0.0 means either direction (drawn); else fixed +1 or -1. */
            double fixedDirection,
            String bible,
            List<Beat> beats) {
    }

    static final List<Entry> ENTRIES = List.of(
            new Entry("takeover", NewsSource.THE_WIRE, NewsScope.COMPANY,
                    0.28, 0.35, 1.0,
                    "Suitor: Halcyon Capital. Advisers: Beacon and Company. Board meets Friday.",
                    List.of(
                            new Beat("{name} receives takeover approach at a fat premium",
                                    "Takeover approach reported for {name}.",
                                    0.5, 0.5, 0.05, 0.08, 1.0),
                            new Beat("Bidding war erupts over {name} as rival suitor emerges",
                                    "Rival bid reported for {name}.",
                                    0.3, 0.6, 0.03, 0.06, 1.0),
                            new Beat("{name} takeover reaches the endgame",
                                    "Board decision due on {name}.",
                                    0.2, 0.8, 0.02, 0.04, 0.65))),
            new Entry("scandal", NewsSource.MERIDIAN_NEWS, NewsScope.COMPANY,
                    0.22, 0.28, -1.0, "Auditors: Brass and Slate. A whistleblower in accounting.",
                    List.of(
                            new Beat("Auditors refuse to sign off on {name} books",
                                    "Accounting questions mount at {name}.",
                                    1.0, 0.6, 0.03, 0.06, 0.70))),
            new Entry("crash", NewsSource.TAPE, NewsScope.MACRO,
                    0.10, 0.12, -1.0, "Desks blame program selling into thin books.",
                    List.of(
                            new Beat("Flash crash sweeps the tape",
                                    "Sudden air pocket hits the market.",
                                    1.0, 0.7, 0.02, 0.04, 0.25))),
            new Entry("squeeze", NewsSource.TAPE, NewsScope.COMPANY,
                    0.18, 0.22, 1.0, "Borrow desks ran dry mid-morning.",
                    List.of(
                            new Beat("Shorts scramble to cover {name}",
                                    "Short squeeze erupts in {name}.",
                                    1.0, 0.8, 0.03, 0.06, 1.0))),
            new Entry("bank", NewsSource.THE_WIRE, NewsScope.MACRO,
                    0.09, 0.12, 0.0, "Decision split the committee.",
                    List.of(
                            new Beat("Central bank shocks the market",
                                    "Surprise policy move reprices everything.",
                                    1.0, 0.5, 0.05, 0.08, 0.80))),
            new Entry("meme", NewsSource.TAPE, NewsScope.COMPANY,
                    0.13, 0.17, 1.0, "Forums piled in before noon.",
                    List.of(
                            new Beat("Stampede piles into {name}",
                                    "Meme frenzy lifts {name}.",
                                    1.0, 0.9, 0.03, 0.06, 1.0))),
            new Entry("probe", NewsSource.MERIDIAN_NEWS, NewsScope.COMPANY,
                    0.15, 0.20, -1.0, "Regulator: the Exchange Oversight Panel. Chief placed on leave.",
                    List.of(
                            new Beat("Fraud probe opens on {name}, chief ousted",
                                    "Regulators probe {name}; chief executive out.",
                                    1.0, 0.6, 0.03, 0.06, 0.70))));

    private ScenarioDeck() {
    }

    /** Canon facts the narrator may use for a story; empty when it has none. */
    public static String bibleForStory(String storyId) {
        if (storyId == null) {
            return "";
        }
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("^scen-(.+)-\\d+$").matcher(storyId);
        if (!matcher.matches()) {
            return "";
        }
        String key = matcher.group(1);
        return ENTRIES.stream()
                .filter(entry -> entry.key().equals(key))
                .map(Entry::bible)
                .findFirst()
                .orElse("");
    }
}
