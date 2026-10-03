package com.virtualinvestor.core.calibration;

import com.virtualinvestor.core.fundamental.FundamentalModel;
import com.virtualinvestor.core.fundamental.OrderFlowImpact;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.random.RandomStreams;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Order-flow impact calibration (task 4.2). Runs a synthetic instrument with a single impulse of
 * buy flow and reports the permanent level and how the transient part decays, so the impact and
 * reversion dials can be tuned.
 *
 * <pre>{@code
 * cd server && mvn -q -pl core compile && \
 *   java -cp core/target/classes com.virtualinvestor.core.calibration.ImpactCalibration
 * }</pre>
 */
public final class ImpactCalibration {

    private static final long STEP_MILLIS = 100L;
    private static final long YEAR_MILLIS = 100_000L;
    private static final double PERMANENT = 5.0;
    private static final double TRANSIENT = 20.0;
    private static final double TRANSIENT_DECAY = 0.97;
    private static final double REVERSION = 0.02;
    private static final double FLOW = 100.0;
    private static final double LIQUIDITY = 1_000_000.0;

    private ImpactCalibration() {
    }

    public static void main(String[] args) {
        Instrument instrument = new Instrument("CAL", "Calibration", "Tech",
                com.virtualinvestor.core.instrument.InstrumentType.EQUITY,
                1_000_000L, 1.0, 0.01, 1L, 100.0, 0.0, 0.0, 0.0);
        FundamentalModel model = new FundamentalModel(
                InstrumentUniverse.of(java.util.List.of(instrument)), new RandomStreams(1L), 0.0, 0.0,
                STEP_MILLIS, YEAR_MILLIS);
        Map<String, Double> liquidity = new LinkedHashMap<>();
        liquidity.put("CAL", LIQUIDITY);
        model.setOrderFlowImpact(new OrderFlowImpact(PERMANENT, TRANSIENT, TRANSIENT_DECAY, liquidity));
        model.setReversionStrength(REVERSION);

        double reference = model.reference("CAL");
        double moved = model.applyFlow("CAL", FLOW);
        double peakGap = moved / reference - 1.0;

        System.out.printf(Locale.ROOT, "peGapAfterImpulse=%.8f%n", peakGap);
        int[] checkpoints = {1, 5, 20, 100, 500};
        for (int target : checkpoints) {
            while (true) {
                model.step();
                if (currentStep(target)) {
                    break;
                }
            }
            double gap = model.fundamental("CAL") / model.reference("CAL") - 1.0;
            System.out.printf(Locale.ROOT, "gapAfterSteps[%d]=%.8f%n", target, gap);
        }
        System.out.printf(Locale.ROOT, "permanentCoefficient=%.6f%n", PERMANENT);
        System.out.printf(Locale.ROOT, "transientCoefficient=%.6f%n", TRANSIENT);
        System.out.printf(Locale.ROOT, "transientDecay=%.4f%n", TRANSIENT_DECAY);
        System.out.printf(Locale.ROOT, "reversionStrength=%.4f%n", REVERSION);
    }

    private static int stepCounter = 0;

    private static boolean currentStep(int target) {
        return ++stepCounter == target;
    }
}
