package com.virtualinvestor.core.instrument;

/**
 * A tradeable (or reference) instrument in the fictional universe. Exposures are the
 * factor-model loadings the fundamental-value process uses: {@code marketBeta} to the
 * market factor and {@code sectorBeta} to the instrument's sector factor.
 */
public record Instrument(
        String ticker,
        String name,
        String sector,
        InstrumentType type,
        long sharesOutstanding,
        double freeFloat,
        double tickSize,
        long quantityGrid,
        double referencePrice,
        double baseVolatility,
        double marketBeta,
        double sectorBeta) {

    public Instrument {
        requireText(ticker, "ticker");
        requireText(name, "name");
        requireText(sector, "sector");
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        if (sharesOutstanding < 0L) {
            throw new IllegalArgumentException("sharesOutstanding must not be negative");
        }
        if (freeFloat < 0.0 || freeFloat > 1.0) {
            throw new IllegalArgumentException("freeFloat must be within [0, 1], was " + freeFloat);
        }
        if (tickSize <= 0.0) {
            throw new IllegalArgumentException("tickSize must be positive, was " + tickSize);
        }
        if (quantityGrid <= 0L) {
            throw new IllegalArgumentException("quantityGrid must be positive, was " + quantityGrid);
        }
        if (referencePrice <= 0.0) {
            throw new IllegalArgumentException("referencePrice must be positive, was " + referencePrice);
        }
        if (baseVolatility < 0.0) {
            throw new IllegalArgumentException("baseVolatility must not be negative");
        }
    }

    public boolean isIndex() {
        return type == InstrumentType.INDEX;
    }

    public boolean isSeries() {
        return type == InstrumentType.FUTURE || type == InstrumentType.OPTION;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
