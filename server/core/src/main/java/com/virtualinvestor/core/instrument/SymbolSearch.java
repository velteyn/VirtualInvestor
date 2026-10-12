package com.virtualinvestor.core.instrument;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

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

    /**
     * The listed derivative chain for one underlying: its future first, then calls by
     * ascending strike, then puts by ascending strike. Empty when the underlying is
     * unknown or has no listed series.
     */
    public List<Instrument> chain(String underlyingTicker) {
        if (underlyingTicker == null || underlyingTicker.isBlank()) {
            return List.of();
        }
        List<Instrument> futures = new ArrayList<>();
        List<Instrument> calls = new ArrayList<>();
        List<Instrument> puts = new ArrayList<>();
        for (Instrument instrument : universe.all()) {
            Optional<SeriesSymbols.SeriesTerms> terms = SeriesSymbols.parse(instrument.ticker());
            if (terms.isEmpty() || !terms.get().underlying().equalsIgnoreCase(underlyingTicker)) {
                continue;
            }
            switch (terms.get().kind()) {
                case FUTURE -> futures.add(instrument);
                case CALL -> calls.add(instrument);
                case PUT -> puts.add(instrument);
            }
        }
        calls.sort(Comparator.comparingDouble(instrument ->
                SeriesSymbols.parse(instrument.ticker()).orElseThrow().strike()));
        puts.sort(Comparator.comparingDouble(instrument ->
                SeriesSymbols.parse(instrument.ticker()).orElseThrow().strike()));
        List<Instrument> chain = new ArrayList<>(futures.size() + calls.size() + puts.size());
        chain.addAll(futures);
        chain.addAll(calls);
        chain.addAll(puts);
        return List.copyOf(chain);
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
