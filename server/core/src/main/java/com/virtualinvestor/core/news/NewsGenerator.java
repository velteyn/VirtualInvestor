package com.virtualinvestor.core.news;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.domain.NewsStage;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.random.DeterministicRandom;
import com.virtualinvestor.core.random.RandomStreams;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Builds a round's news timeline: one scheduled earnings event per equity, plus random events
 * whose count scales with the session length. All draws come from the "news.random" stream of
 * the round seed, so the same seed produces the same events at the same ticks.
 */
public final class NewsGenerator {

    private final InstrumentUniverse universe;
    private final RandomStreams streams;
    private final long ticksPerRandomEvent;
    private final double earningsFraction;

    /**
     * Reaction-validity window in ticks (news-validity 2.1): how long a headline
     * stays tradeable scales with what the news is — magnitude times a scope
     * weight, as a fraction of the session. Earnings burn out in days; big macro
     * shocks stay tradeable nearly all game. Floored so every headline is at
     * least barely reactable, capped at the session itself.
     */
    static long validityTicks(long sessionTicks, NewsScope scope, double magnitude) {
        double weight = switch (scope) {
            case COMPANY -> 5.0;
            case SECTOR -> 8.0;
            default -> 15.0;
        };
        return Math.max(5L, Math.min(sessionTicks, Math.round(sessionTicks * magnitude * weight)));
    }

    public NewsGenerator(
            InstrumentUniverse universe, RandomStreams streams, long ticksPerRandomEvent, double earningsFraction) {
        this.universe = Objects.requireNonNull(universe, "universe");
        this.streams = Objects.requireNonNull(streams, "streams");
        if (ticksPerRandomEvent <= 0L) {
            throw new IllegalArgumentException("ticksPerRandomEvent must be positive");
        }
        if (earningsFraction < 0.0 || earningsFraction >= 1.0) {
            throw new IllegalArgumentException("earningsFraction must be within [0, 1)");
        }
        this.ticksPerRandomEvent = ticksPerRandomEvent;
        this.earningsFraction = earningsFraction;
    }

    public List<NewsItem> generate(long startTick, long sessionTicks) {
        if (sessionTicks <= 0L) {
            throw new IllegalArgumentException("sessionTicks must be positive");
        }
        List<NewsItem> items = new ArrayList<>();
        DeterministicRandom random = streams.stream("news.random");
        // Tiers roll off their own stream (news.tiers), so adding reliability
        // never shifts the event stream: same seed, same events at same ticks.
        DeterministicRandom tiers = streams.stream("news.tiers");
        // Pack draws (news.packs) are content selection like entries: a
        // separate stream so lede wording never shifts event timing.
        DeterministicRandom packs = streams.stream("news.packs");

        List<Instrument> equities = universe.equities();
        for (int i = 0; i < equities.size(); i++) {
            Instrument instrument = equities.get(i);
            double fraction = Math.min(0.98, earningsFraction + 0.02 * (i % 5));
            long tick = startTick + (long) (sessionTicks * fraction);
            boolean beat = random.nextDouble() < 0.5;
            double direction = beat ? 1.0 : -1.0;
            double magnitude = 0.01 + random.nextDouble() * 0.03;
            // Earnings keep their single headline; the body comes from the
            // analyst pack in the beat's direction (no resolution ledes).
            StoryTemplates.Triple pack = StoryTemplates.drawFor(
                    NewsScope.COMPANY, direction, packs);
            items.add(new NewsItem(
                    "sched-" + instrument.ticker(),
                    NewsSource.THE_WIRE,
                    ReliabilityTier.roll(tiers.nextDouble()),
                    NewsScope.COMPANY,
                    instrument.ticker(),
                    instrument.sector(),
                    instrument.name() + (beat ? " beats quarterly expectations" : " misses quarterly expectations"),
                    StoryTemplates.render(pack.body(), instrument.name(), instrument.sector()),
                    direction,
                    magnitude,
                    validityTicks(sessionTicks, NewsScope.COMPANY, magnitude),
                    NewsStage.CONFIRMED,
                    tick,
                    0.5,
                    sessionTicks / 10L,
                    false,
                    null,
                    null));
        }

        int randomCount = (int) (sessionTicks / ticksPerRandomEvent);
        for (int i = 0; i < randomCount; i++) {
            long tick = startTick + (long) Math.floor(random.nextDouble() * sessionTicks);
            int scopeRoll = random.nextInt(100);
            double direction = random.nextDouble() < 0.5 ? 1.0 : -1.0;
            double magnitude = 0.005 + random.nextDouble() * 0.02;
            Instrument instrument = equities.get(random.nextInt(equities.size()));

            NewsScope scope;
            String symbol = null;
            String sector = null;
            if (scopeRoll < 55) {
                scope = NewsScope.COMPANY;
                symbol = instrument.ticker();
            } else if (scopeRoll < 80) {
                scope = NewsScope.SECTOR;
                sector = instrument.sector();
            } else {
                scope = NewsScope.MACRO;
            }
            // Staged from the pack: rumor, confirm and deny ledes plus the
            // body all come from one entry in the rumor's direction, so the
            // resolution never repeats the rumor text.
            StoryTemplates.Triple pack = StoryTemplates.drawFor(scope, direction, packs);
            String slotName = instrument.name();
            String slotSector = scope == NewsScope.SECTOR ? sector : instrument.sector();
            items.add(new NewsItem(
                    "rand-" + i,
                    NewsSource.TAPE,
                    ReliabilityTier.roll(tiers.nextDouble()),
                    scope,
                    symbol,
                    sector,
                    StoryTemplates.render(pack.rumor(), slotName, slotSector),
                    StoryTemplates.render(pack.body(), slotName, slotSector),
                    direction,
                    magnitude,
                    validityTicks(sessionTicks, scope, magnitude),
                    NewsStage.RUMOR,
                    tick,
                    0.5,
                    sessionTicks / 10L,
                    false,
                    StoryTemplates.render(pack.confirm(), slotName, slotSector),
                    StoryTemplates.render(pack.deny(), slotName, slotSector)));
        }

        List<NewsItem> scenarios = planScenarios(random, tiers, packs, equities, startTick, sessionTicks);
        java.util.Set<String> scenarioIds = new java.util.HashSet<>();
        for (NewsItem scenario : scenarios) {
            scenarioIds.add(scenario.id());
            items.add(scenario);
        }

        // Room banter: zero-magnitude macro items at fixed session fractions,
        // day-gated so mid-game banter unlocks as the session ages. Flavor
        // only — no fundamental impact, ignored by every strategy.
        for (int t = 0; t < TAUNT_FRACTIONS.length; t++) {
            double fraction = TAUNT_FRACTIONS[t];
            Optional<String> taunt = StoryTemplates.tauntAt(fraction, packs);
            if (taunt.isEmpty()) {
                continue;
            }
            items.add(new NewsItem(
                    "taunt-" + t,
                    NewsSource.TAPE,
                    ReliabilityTier.roll(tiers.nextDouble()),
                    NewsScope.MACRO,
                    null,
                    null,
                    taunt.get(),
                    "",
                    0.0,
                    0.0,
                    validityTicks(sessionTicks, NewsScope.MACRO, 0.0),
                    NewsStage.RUMOR,
                    startTick + (long) (sessionTicks * fraction),
                    0.5,
                    sessionTicks / 10L,
                    false,
                    null,
                    null));
        }

        items.sort(Comparator.comparingLong(NewsItem::tick).thenComparing(NewsItem::id));
        // Legacy tiered staging for everything the deck did not stage: the
        // rumor keeps its wire shape (fraction 1/2, session/10 delay) while
        // confirmation is a tier-biased draw off the news stream — wire ledes
        // confirm most often, spy ledes deny most often.
        List<NewsItem> staged = new ArrayList<>(items.size());
        for (int index = 0; index < items.size(); index++) {
            NewsItem item = items.get(index);
            if (scenarioIds.contains(item.id())) {
                staged.add(item);
                continue;
            }
            staged.add(new NewsItem(
                    item.id(), item.source(), item.tier(), item.scope(), item.symbol(), item.sector(),
                    item.headline(), item.body(), item.direction(), item.magnitude(),
                    item.durationTicks(), item.stage(), item.tick(),
                    0.5, sessionTicks / 10L, random.nextDouble() < item.tier().confirmBias(),
                    item.confirmHeadline(), item.denyHeadline()));
        }
        return List.copyOf(staged);
    }

    /**
     * Rare staged scenarios (market-scenarios): takeover bids, scandals, crashes,
     * squeezes, macro shocks and stampedes as rumor → confirm/deny arcs. Fully
     * staged here (fraction, delay, confirm draw); the runtime stages every
     * other item with the legacy uniform mapping.
     */
    static List<NewsItem> planScenarios(
            DeterministicRandom random, DeterministicRandom tiers, DeterministicRandom packs,
            List<Instrument> equities, long startTick, long sessionTicks) {
        int count = 1 + (int) Math.floor(random.nextDouble() * 3.0);
        List<NewsItem> scenarios = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ScenarioDeck.Entry entry = ScenarioDeck.ENTRIES.get(random.nextInt(ScenarioDeck.ENTRIES.size()));
            Instrument instrument = equities.get(random.nextInt(equities.size()));
            double total = entry.minMagnitude()
                    + random.nextDouble() * (entry.maxMagnitude() - entry.minMagnitude());
            double direction = entry.fixedDirection() == 0.0
                    ? (random.nextDouble() < 0.5 ? 1.0 : -1.0)
                    : entry.fixedDirection();
            String storyId = "scen-" + entry.key() + "-" + i;
            // One story, one source, one reliability tier for all its beats.
            ReliabilityTier tier = ReliabilityTier.roll(tiers.nextDouble());
            // Beats chain forward: each rumor lands just after the previous
            // beat's resolution, so the fundamental path stays continuous.
            long tick = startTick + (long) (sessionTicks * Math.min(scenarioBase(i, random), 0.94));
            List<ScenarioDeck.Beat> beats = entry.beats();
            for (int b = 0; b < beats.size(); b++) {
                ScenarioDeck.Beat beat = beats.get(b);
                // Beats never run past the close; in a cramped slot they bunch
                // (tick order still holds, ids sort the ties).
                tick = Math.min(tick, startTick + (long) (sessionTicks * 0.96));
                double magnitude = total * beat.magnitudeShare();
                long delay = (long) (sessionTicks * (beat.minDelayFraction()
                        + random.nextDouble() * (beat.maxDelayFraction() - beat.minDelayFraction())));
                long resolutionTick = Math.min(tick + delay, startTick + (long) (sessionTicks * 0.98));
                // Middle beats always continue the arc; only the finale draws
                // its fate from the deck's own bias, decoupled from the tier
                // multiplier: a major shock's fate is a market-structure fact,
                // not a function of who carried the rumor. Tiers still govern
                // trust reads and routine-item confirms, so halt-inducing ends
                // stay rare but present and the breaker keeps getting tested.
                boolean confirmed = b < beats.size() - 1 || random.nextDouble() < beat.confirmBias();
                String name = instrument.name();
                String headline = beat.headline().replace("{name}", name).replace("{sector}", instrument.sector());
                String sector = entry.scope() == NewsScope.SECTOR ? instrument.sector() : null;
                // The deck authors the rumor beat (arc continuity + bible);
                // the resolution ledes come from the matching pack so the
                // wire never repeats the rumor text at confirm/deny time.
                StoryTemplates.Triple pack = StoryTemplates.drawScenario(entry.key(), direction, packs);
                scenarios.add(new NewsItem(
                        StoryIds.beatId(storyId, b),
                        entry.source(),
                        tier,
                        entry.scope(),
                        entry.scope() == NewsScope.COMPANY ? instrument.ticker() : null,
                        sector,
                        headline,
                        beat.body().replace("{name}", name).replace("{sector}", instrument.sector()),
                        direction,
                        magnitude,
                        validityTicks(sessionTicks, entry.scope(), magnitude),
                        NewsStage.RUMOR,
                        tick,
                        beat.rumorFraction(),
                        Math.max(1L, resolutionTick - tick),
                        confirmed,
                        StoryTemplates.render(pack.confirm(), name, instrument.sector()),
                        StoryTemplates.render(pack.deny(), name, instrument.sector())));
                tick = resolutionTick + (long) (sessionTicks * 0.01);
            }
        }
        return List.copyOf(scenarios);
    }

    /** Arc start slots: ≥12% in, ≥18% apart, under 94% of the session. */
    private static final double[] TAUNT_FRACTIONS = {0.30, 0.55, 0.80};
    private static double scenarioBase(int index, DeterministicRandom random) {
        return 0.12 + 0.24 * index + random.nextDouble() * 0.06;
    }
}
