package com.virtualinvestor.core.fundamental;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentType;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.random.RandomStreams;
import java.util.List;
import org.junit.jupiter.api.Test;

class FundamentalModelTest {

    private static Instrument instrument(String ticker, double marketBeta, double sectorBeta, double idioVol) {
        return new Instrument(ticker, "Test " + ticker, "Tech", InstrumentType.EQUITY,
                1_000_000L, 1.0, 0.01, 1L, 100.0, idioVol, marketBeta, sectorBeta);
    }

    private static InstrumentUniverse syntheticUniverse() {
        return InstrumentUniverse.of(List.of(
                instrument("HIGH1", 1.8, 0.0, 0.05),
                instrument("HIGH2", 1.8, 0.0, 0.05),
                instrument("LOW1", 0.0, 0.0, 0.30),
                instrument("LOW2", 0.0, 0.0, 0.30)));
    }

    private static final long STEP_MILLIS = 100L;
    private static final long YEAR_MILLIS = 100_000L;
    private static final int STEPS = 20_000;

    @Test
    void highExposureInstrumentsAreCorrelated() {
        InstrumentUniverse universe = syntheticUniverse();
        FundamentalModel model = new FundamentalModel(
                universe, new RandomStreams(99L), 0.30, 0.20, STEP_MILLIS, YEAR_MILLIS);

        double[] first = new double[STEPS];
        double[] second = new double[STEPS];
        for (int i = 0; i < STEPS; i++) {
            model.step();
            first[i] = model.lastReturn("HIGH1");
            second[i] = model.lastReturn("HIGH2");
        }

        double correlation = correlation(first, second);
        assertTrue(correlation > 0.9, () -> "expected high correlation, got " + correlation);
    }

    @Test
    void zeroExposureInstrumentsAreNearlyIndependent() {
        InstrumentUniverse universe = syntheticUniverse();
        FundamentalModel model = new FundamentalModel(
                universe, new RandomStreams(99L), 0.30, 0.20, STEP_MILLIS, YEAR_MILLIS);

        double[] first = new double[STEPS];
        double[] second = new double[STEPS];
        for (int i = 0; i < STEPS; i++) {
            model.step();
            first[i] = model.lastReturn("LOW1");
            second[i] = model.lastReturn("LOW2");
        }

        double correlation = correlation(first, second);
        assertTrue(Math.abs(correlation) < 0.1, () -> "expected near-zero correlation, got " + correlation);
    }

    @Test
    void sameSeedProducesIdenticalFundamentals() {
        double firstValue = runAndRead("HIGH1");
        double secondValue = runAndRead("HIGH1");
        assertEquals(firstValue, secondValue);
    }

    @Test
    void companyNewsJumpMovesOnlyTheTargetedName() {
        FundamentalModel model = new FundamentalModel(
                syntheticUniverse(), new RandomStreams(5L), 0.30, 0.20, STEP_MILLIS, YEAR_MILLIS);
        double high1Before = model.fundamental("HIGH1");
        double high2Before = model.fundamental("HIGH2");
        double low1Before = model.fundamental("LOW1");
        double low2Before = model.fundamental("LOW2");

        List<String> affected = model.applyNews(NewsScope.COMPANY, "HIGH1", "Tech", 0.05);

        assertEquals(List.of("HIGH1"), affected);
        assertEquals(high1Before * 1.05, model.fundamental("HIGH1"), 1e-9);
        assertEquals(high2Before, model.fundamental("HIGH2"), 0.0);
        assertEquals(low1Before, model.fundamental("LOW1"), 0.0);
        assertEquals(low2Before, model.fundamental("LOW2"), 0.0);
    }

    @Test
    void macroNewsScalesByMarketBeta() {
        FundamentalModel model = new FundamentalModel(
                syntheticUniverse(), new RandomStreams(5L), 0.30, 0.20, STEP_MILLIS, YEAR_MILLIS);
        double high1Before = model.fundamental("HIGH1"); // beta 1.8
        double low1Before = model.fundamental("LOW1");   // beta 0.0

        model.applyNews(NewsScope.MACRO, null, null, 0.10);

        assertEquals(high1Before * (1.0 + 0.10 * 1.8), model.fundamental("HIGH1"), 1e-9);
        assertEquals(low1Before, model.fundamental("LOW1"), 1e-9);
    }

    private static double runAndRead(String ticker) {
        FundamentalModel model = new FundamentalModel(
                syntheticUniverse(), new RandomStreams(7L), 0.30, 0.20, STEP_MILLIS, YEAR_MILLIS);
        for (int i = 0; i < 500; i++) {
            model.step();
        }
        return model.fundamental(ticker);
    }

    private static double correlation(double[] a, double[] b) {
        double meanA = 0.0;
        double meanB = 0.0;
        for (int i = 0; i < a.length; i++) {
            meanA += a[i];
            meanB += b[i];
        }
        meanA /= a.length;
        meanB /= b.length;

        double covariance = 0.0;
        double varianceA = 0.0;
        double varianceB = 0.0;
        for (int i = 0; i < a.length; i++) {
            double da = a[i] - meanA;
            double db = b[i] - meanB;
            covariance += da * db;
            varianceA += da * da;
            varianceB += db * db;
        }
        return covariance / Math.sqrt(varianceA * varianceB);
    }
}
