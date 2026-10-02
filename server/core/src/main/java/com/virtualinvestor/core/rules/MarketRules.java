package com.virtualinvestor.core.rules;

import com.virtualinvestor.core.instrument.Instrument;

/**
 * The per-instrument trading rules a venue enforces: price must sit on the tick grid,
 * quantity must respect the lot/quantity grid and a minimum. More rules (price bands,
 * short availability, margin) compose with this in later tasks.
 */
public record MarketRules(double tickSize, long quantityGrid, long minimumQuantity) {

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
    }

    public static MarketRules forInstrument(Instrument instrument) {
        return new MarketRules(instrument.tickSize(), instrument.quantityGrid(), instrument.quantityGrid());
    }
}
