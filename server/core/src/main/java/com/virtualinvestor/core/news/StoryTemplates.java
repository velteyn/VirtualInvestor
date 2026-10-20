package com.virtualinvestor.core.news;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.random.DeterministicRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Authored lede packs (rivals-news 2.1): every staged story renders its
 * rumor/confirm/deny ledes from a pack entry instead of a single headline, so
 * resolutions read as new beats rather than repeated rumors. Entries carry
 * positional {name}/{sector} slots, stay within 200 chars, and name only
 * fictional companies — enforced by {@code StoryTemplatesTest}. Draws come
 * from the caller's stream, so the round seed still decides everything.
 */
public final class StoryTemplates {

    /** One authored story: the triple plus its rumor-stage body. */
    public record Triple(String rumor, String confirm, String deny, String body, double direction) {
        public Triple {
            if (direction != 1.0 && direction != -1.0) {
                throw new IllegalArgumentException("triple direction must be +1 or -1");
            }
        }
    }

    /** Room banter: single ledes gated by session-day fraction, no resolution. */
    public record Taunt(String text, double gateFraction) {
    }

    static final List<Triple> TAKEOVER = List.of(
            new Triple("{name} receives a takeover approach at a fat premium.",
                    "Shareholders approve the {name} takeover at a premium.",
                    "The {name} takeover approach collapses without a bid.",
                    "{name} confirmed contact from a suitor and hired advisers; a formal offer may follow once financing is set.",
                    1.0),
            new Triple("Rival suitors circle {name} as bid talk spreads.",
                    "A bidding war erupts over {name}.",
                    "Suitors walk away from {name}; talks end.",
                    "Two parties signed nondisclosure agreements covering {name}; bankers expect a second round of bids.",
                    1.0),
            new Triple("{name} calls a board meeting to weigh a buyout.",
                    "{name} agrees to a buyout pending a shareholder vote.",
                    "{name} rejects the buyout as too cheap.",
                    "Directors met through the session; advisers value the bid against trading multiples and breakup value.",
                    1.0));

    static final List<Triple> REGULATORY = List.of(
            new Triple("Regulators open a file on {name}.",
                    "Regulators charge {name} after a probe.",
                    "Regulators close the {name} file with no action.",
                    "The oversight panel requested documents on past disclosures; the company says it is cooperating.",
                    -1.0),
            new Triple("{sector} faces a sweeping compliance review.",
                    "{sector} firms face new conduct rules.",
                    "The {sector} review ends with guidance only.",
                    "Supervisors flagged sales practices across {sector}; fines are possible but no timetable was given.",
                    -1.0));

    static final List<Triple> GURU = List.of(
            new Triple("A star manager calls {name} the trade of the year.",
                    "{name} jumps as funds copy the guru call.",
                    "The guru call on {name} fizzles as funds fade it.",
                    "The televised pitch moved retail flows first; institutional desks wait for filings before chasing.",
                    1.0),
            new Triple("Letter writers turn buyers of {name}.",
                    "{name} rallies as letters pile in.",
                    "The letter hype in {name} unwinds fast.",
                    "Influential newsletters upgraded the name on volume growth; momentum desks front-ran the print editions.",
                    1.0),
            new Triple("A star manager shorts {name} on television.",
                    "{name} slides as funds follow the short call.",
                    "The short call on {name} squeezes the skeptics.",
                    "The celebrated short thesis cites channel checks; contrarians note borrow is already crowded.",
                    -1.0),
            new Triple("Letter writers turn sellers of {sector}.",
                    "{sector} drops as letters rush the exits.",
                    "{sector} steadies as the letter panic passes.",
                    "Bearish subscription notes cite stretched multiples; dip buyers absorbed the first wave.",
                    -1.0),
            new Triple("Cable money rotates into {sector}.",
                    "{sector} catches a guru-driven bid.",
                    "The guru bid in {sector} evaporates.",
                    "Television pundits upgraded the group in chorus; flows chased within the hour.",
                    1.0));

    static final List<Triple> ANALYST = List.of(
            new Triple("Analysts lift targets on {name}.",
                    "{name} climbs on a wave of upgrades.",
                    "{name} slips as upgrades fail to stick.",
                    "Three desks raised numbers on stronger volumes; the bear case centers on margin pressure next half.",
                    1.0),
            new Triple("Analysts cut {name} on soft orders.",
                    "{name} sinks after a round of downgrades.",
                    "{name} holds firm despite the downgrades.",
                    "Cuts cite slowing bookings and inventory overhang; holders point to the dividend while they wait.",
                    -1.0),
            new Triple("Strategists turn positive on {sector}.",
                    "{sector} breadth improves on the upgrade cycle.",
                    "{sector} breadth fades despite the upgrades.",
                    "Allocation notes moved overweight; index flows follow with the usual lag.",
                    1.0),
            new Triple("Strategists sour on {sector} valuations.",
                    "{sector} lags as allocations rotate away.",
                    "{sector} finds footing as sellers exhaust.",
                    "Notes flag multiples two turns above history; stock pickers hunt the few cheap names left.",
                    -1.0),
            new Triple("Economists see a soft landing taking hold.",
                    "Data confirms the soft landing; stocks climb.",
                    "Data kills the soft landing story.",
                    "Forecasts cluster around trend growth; the risk case is a hiring freeze feeding into spending.",
                    1.0),
            new Triple("Economists warn the expansion is stalling.",
                    "Weak data confirms the stall; stocks slide.",
                    "Strong data buries the stall fears.",
                    "Leading indicators rolled over two months running; bulls bet the consumer carries one more quarter.",
                    -1.0),
            new Triple("Rate-cut bets lift the tape.",
                    "The tape rallies as cut bets pay off.",
                    "Cut bets unwind; the tape gives it back.",
                    "Futures priced two cuts by year end; every payroll print now moves the needle.",
                    1.0),
            new Triple("Rate-hike chatter spooks the tape.",
                    "Hawkish talk sinks the tape.",
                    "Dovish minutes calm the tape.",
                    "Policymakers stressed patience; traders heard higher for longer.",
                    -1.0));

    static final List<Taunt> TAUNTS = List.of(
            new Taunt("The desk is watching the newcomers. Paper hands get found out by lunch.", 0.15),
            new Taunt("VULTURE is circling the laggards. Somebody is about to learn stop discipline.", 0.35),
            new Taunt("Half the room is green and insufferable about it.", 0.55),
            new Taunt("Late session. Spreads widen, excuses multiply.", 0.75));

    private StoryTemplates() {
    }

    /**
     * A pack entry for a random item of the given scope and direction. Slot
     * discipline keeps ledes on-scope: company ledes name the company, sector
     * ledes name the sector, macro ledes name neither.
     */
    public static Triple drawFor(NewsScope scope, double direction, DeterministicRandom random) {
        List<Triple> pool = new ArrayList<>();
        for (Triple triple : candidates(scope)) {
            if (Math.signum(triple.direction()) != Math.signum(direction)) {
                continue;
            }
            boolean namesCompany = triple.rumor().contains("{name}");
            boolean namesSector = triple.rumor().contains("{sector}");
            boolean fits = switch (scope) {
                case COMPANY -> namesCompany;
                case SECTOR -> !namesCompany && namesSector;
                default -> !namesCompany && !namesSector;
            };
            if (fits) {
                pool.add(triple);
            }
        }
        if (pool.isEmpty()) {
            throw new IllegalStateException("no pack entry for scope " + scope + " direction " + direction);
        }
        return pool.get(random.nextInt(pool.size()));
    }

    /**
     * A pack entry for a scenario finale's confirm/deny ledes, keyed by deck
     * entry with matching polarity. The deck beat keeps its authored rumor
     * lede; only the resolution ledes come from the pack.
     */
    public static Triple drawScenario(String entryKey, double direction, DeterministicRandom random) {
        List<Triple> pack = switch (entryKey) {
            case "takeover" -> TAKEOVER;
            case "scandal", "probe" -> REGULATORY;
            case "squeeze", "meme" -> GURU;
            default -> ANALYST;
        };
        List<Triple> pool = new ArrayList<>();
        for (Triple triple : pack) {
            if (Math.signum(triple.direction()) == Math.signum(direction)) {
                pool.add(triple);
            }
        }
        if (pool.isEmpty()) {
            pool.addAll(pack);
        }
        return pool.get(random.nextInt(pool.size()));
    }

    /** A banter lede unlocked by the session-day fraction, if any qualifies. */
    public static Optional<String> tauntAt(double dayFraction, DeterministicRandom random) {
        List<Taunt> open = new ArrayList<>();
        for (Taunt taunt : TAUNTS) {
            if (taunt.gateFraction() <= dayFraction) {
                open.add(taunt);
            }
        }
        if (open.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(open.get(random.nextInt(open.size())).text());
    }

    /** Fill {name}/{sector} slots (null-safe) and tidy whitespace. */
    public static String render(String template, String name, String sector) {
        String filled = Objects.requireNonNull(template, "template")
                .replace("{name}", name == null ? "" : name)
                .replace("{sector}", sector == null ? "" : sector);
        return filled.replaceAll("\\s+", " ").trim();
    }

    private static List<Triple> candidates(NewsScope scope) {
        return switch (scope) {
            case COMPANY -> concat(TAKEOVER, REGULATORY, GURU, ANALYST);
            case SECTOR -> concat(REGULATORY, GURU, ANALYST);
            default -> concat(GURU, ANALYST);
        };
    }

    private static List<Triple> concat(List<Triple>... packs) {
        List<Triple> all = new ArrayList<>();
        for (List<Triple> pack : packs) {
            all.addAll(pack);
        }
        return all;
    }
}
