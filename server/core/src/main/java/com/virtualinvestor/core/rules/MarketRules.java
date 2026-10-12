package com.virtualinvestor.core.rules;

import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.SeriesLister;
import com.virtualinvestor.core.instrument.SeriesSymbols;
import java.util.Optional;

/**
 * The per-instrument trading rules a venue enforces: price must sit on the tick grid,
 * quantity must respect the lot/quantity grid and a minimum. Derivative series
 * additionally carry their underlying (for halt propagation) and margin fractions.
 * Spot instruments are margined by the round preset, so their fractions are zero
 * (not applicable) here. More rules (price bands, short availability) compose with
 * this in later tasks.
 */
public record MarketRules(
        double tickSize,
        long quantityGrid,
        long minimumQuantity,
        String underlyingTicker,
        double initialMarginFraction,
        double maintenanceMarginFraction) {

    public MarketRules {
        if (tickSize <= 0.0) {
            throw new IllegalArgumentException("tickSize must be positive, was " + tickSize);
        }
        if (quantityGrid <= 0L) {
            throw new IllegalArgumentException("quantityGrid must be positive, was " + quantityGrid);
        }
        if (minimumQuantity <= 0L) {
            throw new IllegalArgumentException("minimumQuantity must be positive, was " + minimumQuantity);
        }
        if (initialMarginFraction < 0.0 || initialMarginFraction > 1.0) {
            throw new IllegalArgumentException(
                    "initialMarginFraction must be within [0, 1], was " + initialMarginFraction);
        }
        if (maintenanceMarginFraction < 0.0 || maintenanceMarginFraction > 1.0) {
            throw new IllegalArgumentException(
                    "maintenanceMarginFraction must be within [0, 1], was " + maintenanceMarginFraction);
        }
    }

    public static MarketRules forInstrument(Instrument instrument) {
        return new MarketRules(
                instrument.tickSize(), instrument.quantityGrid(), instrument.quantityGrid(), null, 0.0, 0.0);
    }

    /**
     * Rules for a listed derivative series: grids from the series, the underlying for
     * halt propagation, and margins floored at the series default (WST canon: 20%
     * initial) so a lenient preset cannot under-margin levered exposure.
     */
    public static MarketRules forSeries(Instrument series, RealismPreset preset) {
        SeriesSymbols.SeriesTerms terms = SeriesSymbols.parse(series.ticker())
                .orElseThrow(() -> new IllegalArgumentException("not a listed series: " + series.ticker()));
        return new MarketRules(
                series.tickSize(),
                series.quantityGrid(),
                series.quantityGrid(),
                terms.underlying(),
                Math.max(SeriesLister.DEFAULT_INITIAL_MARGIN, preset.initialMarginFraction()),
                preset.maintenanceMarginFraction());
    }

    public boolean isSeries() {
        return underlyingTicker != null;
    }

    /** The underlying whose halt suspends this series, or empty for spot instruments. */
    public Optional<String> propagatesHaltFrom() {
        return Optional.ofNullable(underlyingTicker);
    }
}
