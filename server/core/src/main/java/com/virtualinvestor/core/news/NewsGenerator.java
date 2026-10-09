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

        List<Instrument> equities = universe.equities();
        for (int i = 0; i < equities.size(); i++) {
            Instrument instrument = equities.get(i);
            double fraction = Math.min(0.98, earningsFraction + 0.02 * (i % 5));
            long tick = startTick + (long) (sessionTicks * fraction);
            boolean beat = random.nextDouble() < 0.5;
            double direction = beat ? 1.0 : -1.0;
            double magnitude = 0.01 + random.nextDouble() * 0.03;
            items.add(new NewsItem(
                    "sched-" + instrument.ticker(),
                    NewsSource.THE_WIRE,
                    NewsScope.COMPANY,
                    instrument.ticker(),
                    instrument.sector(),
                    instrument.name() + (beat ? " beats quarterly expectations" : " misses quarterly expectations"),
                    "Scheduled earnings release for " + instrument.name(),
                    direction,
                    magnitude,
                    validityTicks(sessionTicks, NewsScope.COMPANY, magnitude),
                    NewsStage.CONFIRMED,
                    tick));
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
            String headline;
            if (scopeRoll < 55) {
                scope = NewsScope.COMPANY;
                symbol = instrument.ticker();
                headline = instrument.name() + (direction > 0 ? " jumps on upgrade" : " slides on downgrade");
            } else if (scopeRoll < 80) {
                scope = NewsScope.SECTOR;
                sector = instrument.sector();
                headline = instrument.sector() + " names " + (direction > 0 ? "strengthen" : "weaken on flows");
            } else {
                scope = NewsScope.MACRO;
                headline = direction > 0 ? "Data beats expectations, stocks climb" : "Data misses, stocks slide";
            }
            items.add(new NewsItem(
                    "rand-" + i,
                    NewsSource.TAPE,
                    scope,
                    symbol,
                    sector,
                    headline,
                    "Wire report",
                    direction,
                    magnitude,
                    validityTicks(sessionTicks, scope, magnitude),
                    NewsStage.RUMOR,
                    tick));
        }

        items.sort(Comparator.comparingLong(NewsItem::tick).thenComparing(NewsItem::id));
        return List.copyOf(items);
    }
}
