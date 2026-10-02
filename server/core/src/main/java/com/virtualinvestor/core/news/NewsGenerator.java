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
                    sessionTicks / 4L,
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
                    600L + random.nextInt(4) * 300L,
                    NewsStage.RUMOR,
                    tick));
        }

        items.sort(Comparator.comparingLong(NewsItem::tick).thenComparing(NewsItem::id));
        return List.copyOf(items);
    }
}
