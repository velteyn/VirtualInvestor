package com.virtualinvestor.core.fundamental;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-symbol order-flow toxicity (realistic-liquidity 2.1): how dangerous the
 * tape looks to a liquidity provider. One-sided aggressive flow and realized
 * volatility raise it; calm decays it exponentially. Liquidity-scaled like
 * {@link OrderFlowImpact} so one threshold means the same everywhere.
 *
 * <p>Deterministic: plain function of the observed flow, so replays agree.
 */
public final class FlowToxicity {

    private final double flowWeight;
    private final double volatilityWeight;
    private final double decay;
    private final Map<String, Double> liquidity;

    private final Map<String, Double> score = new LinkedHashMap<>();

    public FlowToxicity(double flowWeight, double volatilityWeight, double decay,
            Map<String, Double> liquidity) {
        if (flowWeight < 0.0 || volatilityWeight < 0.0) {
            throw new IllegalArgumentException("weights must not be negative");
        }
        if (decay < 0.0 || decay > 1.0) {
            throw new IllegalArgumentException("decay must be within [0, 1]");
        }
        this.flowWeight = flowWeight;
        this.volatilityWeight = volatilityWeight;
        this.decay = decay;
        this.liquidity = new LinkedHashMap<>(liquidity);
    }

    /** Fold one tick's signed flow and realized volatility into the symbol's score. */
    public void observe(String symbol, double signedFlow, double realizedVolatility) {
        double scale = 1.0 / Math.max(1e-9, liquidity.getOrDefault(symbol, 1.0));
        double impulse = flowWeight * Math.abs(signedFlow) * scale + volatilityWeight * realizedVolatility;
        score.merge(symbol, impulse, (current, kick) -> current * decay + kick);
    }

    /** Current toxicity (>= 0); 0 means a calm book. */
    public double toxicity(String symbol) {
        return score.getOrDefault(symbol, 0.0);
    }
}
