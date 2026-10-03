package com.virtualinvestor.core.fundamental;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Order-flow impact (design D1/D2/D3): net aggressive flow moves an instrument's latent
 * fundamental value. Impact has a <em>permanent</em> component that stays and a
 * <em>transient</em> component that decays and reverts each step. The size of the impact is
 * scaled by liquidity, so an illiquid instrument moves more per unit of flow than a liquid one.
 *
 * <p>Deterministic: the state is a plain function of the flow it is fed, so a replay reproduces
 * it exactly.
 */
public final class OrderFlowImpact {

    private final double permanentCoefficient;
    private final double transientCoefficient;
    private final double transientDecay;
    private final Map<String, Double> liquidity;

    private final Map<String, Double> transientOffset = new LinkedHashMap<>();

    /**
     * @param permanentCoefficient fundamental fraction moved permanently per unit of liquidity-
     *                             scaled net flow
     * @param transientCoefficient fundamental fraction moved transiently per unit of flow
     * @param transientDecay       fraction of the transient offset that remains after one step
     *                             (0..1; lower decays faster)
     * @param liquidity            per-instrument liquidity scale; higher means a smaller impact
     */
    public OrderFlowImpact(
            double permanentCoefficient,
            double transientCoefficient,
            double transientDecay,
            Map<String, Double> liquidity) {
        if (permanentCoefficient < 0.0 || transientCoefficient < 0.0) {
            throw new IllegalArgumentException("impact coefficients must not be negative");
        }
        if (transientDecay < 0.0 || transientDecay > 1.0) {
            throw new IllegalArgumentException("transientDecay must be within [0, 1]");
        }
        this.permanentCoefficient = permanentCoefficient;
        this.transientCoefficient = transientCoefficient;
        this.transientDecay = transientDecay;
        this.liquidity = new LinkedHashMap<>(liquidity);
    }

    /** An inert impact (no effect) for tests and for disabling the feature. */
    public static OrderFlowImpact inert() {
        return new OrderFlowImpact(0.0, 0.0, 1.0, Map.of());
    }

    /**
     * Apply one step's flow to an instrument's fundamental value and return the new value.
     *
     * @param fundamental      current fundamental value
     * @param instrument       the instrument the flow is in
     * @param netFlow          signed aggressive flow (positive buy, negative sell); the sign
     *                         carries direction and the magnitude the size
     * @return the fundamental after this step's flow impact
     */
    public double apply(double fundamental, String instrument, double netFlow) {
        double scale = 1.0 / Math.max(1e-9, liquidity.getOrDefault(instrument, 1.0));
        double permanent = permanentCoefficient * netFlow * scale;
        double transientNow = transientCoefficient * netFlow * scale;

        double carried = transientOffset.getOrDefault(instrument, 0.0) * transientDecay;
        double offset = carried + transientNow;
        transientOffset.put(instrument, offset);

        return Math.max(0.01, fundamental * (1.0 + permanent + transientNow));
    }

    public double permanentCoefficient() {
        return permanentCoefficient;
    }

    public double transientCoefficient() {
        return transientCoefficient;
    }

    public double transientDecay() {
        return transientDecay;
    }

    /** The decaying transient offset currently carried for an instrument. */
    public double transientOffset(String instrument) {
        return transientOffset.getOrDefault(instrument, 0.0);
    }
}
