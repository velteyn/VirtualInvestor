package com.virtualinvestor.core.instrument;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Ranks universe instruments against a typed query so the terminal's symbol picker can find
 * them. Ranking is deterministic: exact ticker, then ticker prefix, then name prefix, then
 * substring; ties break on ticker.
 */
public final class SymbolSearch {

    private static final int NO_MATCH = Integer.MAX_VALUE;

    private final InstrumentUniverse universe;

    public SymbolSearch(InstrumentUniverse universe) {
        this.universe = Objects.requireNonNull(universe, "universe");
    }

    public List<Instrument> search(String query, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive, was " + limit);
        }
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);

        List<Scored> matches = new ArrayList<>();
        for (Instrument instrument : universe.all()) {
            int score = score(instrument, needle);
            if (score != NO_MATCH) {
                matches.add(new Scored(instrument, score));
            }
        }
        matches.sort(Comparator.comparingInt(Scored::score).thenComparing(scored -> scored.instrument().ticker()));
        return matches.stream().limit(limit).map(Scored::instrument).toList();
    }

    private static int score(Instrument instrument, String needle) {
        if (needle.isEmpty()) {
            return 4;
        }
        String ticker = instrument.ticker().toLowerCase(Locale.ROOT);
        String name = instrument.name().toLowerCase(Locale.ROOT);
        if (ticker.equals(needle)) {
            return 0;
        }
        if (ticker.startsWith(needle)) {
            return 1;
        }
        if (name.startsWith(needle)) {
            return 2;
        }
        if (ticker.contains(needle)) {
            return 3;
        }
        if (name.contains(needle)) {
            return 4;
        }
        return NO_MATCH;
    }

    private record Scored(Instrument instrument, int score) {
    }
}
