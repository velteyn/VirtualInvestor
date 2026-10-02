package com.virtualinvestor.core.calibration;

import com.virtualinvestor.core.fundamental.FundamentalModel;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.random.RandomStreams;
import java.util.List;
import java.util.Locale;

/**
 * Offline calibration harness (tasks 3.5, 4.5, 12.5). Runs the fundamental model for a fixed
 * seed and reports the stylized facts a credible market should show: return distribution,
 * autocorrelation and cross-correlation. Run it with:
 *
 * <pre>{@code
 * cd server && mvn -q -pl core compile && \
 *   java -cp core/target/classes com.virtualinvestor.core.calibration.Calibration
 * }</pre>
 */
public final class Calibration {

    private static final long STEP_MILLIS = 100L;
    private static final long YEAR_MILLIS = 5_896_800_000L;
    private static final double MARKET_ANNUAL_VOL = 0.30;
    private static final double SECTOR_ANNUAL_VOL = 0.20;
    private static final long SEED = 20261002L;
    private static final int STEPS = 200_000;

    public record Report(
            int steps,
            double mean,
            double std,
            double skew,
            double kurtosis,
            double autocorrLag1,
            double averagePairwiseCorrelation,
            double annualizedMarketVol) {
    }

    private Calibration() {
    }

    public static Report run() {
        InstrumentUniverse universe = InstrumentUniverse.defaultUniverse();
        FundamentalModel model = new FundamentalModel(
                universe, new RandomStreams(SEED), MARKET_ANNUAL_VOL, SECTOR_ANNUAL_VOL, STEP_MILLIS, YEAR_MILLIS);
        List<Instrument> equities = universe.equities();

        double[] marketReturn = new double[STEPS];
        double[][] returns = new double[equities.size()][STEPS];

        for (int step = 0; step < STEPS; step++) {
            model.step();
            double sum = 0.0;
            for (int i = 0; i < equities.size(); i++) {
                double r = model.lastReturn(equities.get(i).ticker());
                returns[i][step] = r;
                sum += r;
            }
            marketReturn[step] = sum / equities.size();
        }

        double mean = mean(marketReturn);
        double std = std(marketReturn, mean);
        double skew = skew(marketReturn, mean, std);
        double kurtosis = kurtosis(marketReturn, mean, std);
        double autocorr = correlation(marketReturn, 1, marketReturn, 0, marketReturn.length - 1);

        double pairSum = 0.0;
        int pairCount = 0;
        for (int i = 0; i < returns.length; i++) {
            for (int j = i + 1; j < returns.length; j++) {
                pairSum += correlation(returns[i], 0, returns[j], 0, STEPS);
                pairCount++;
            }
        }
        double averagePairwise = pairCount == 0 ? 0.0 : pairSum / pairCount;

        double annualizedMarketVol = std / Math.sqrt((double) STEP_MILLIS / YEAR_MILLIS);
        return new Report(STEPS, mean, std, skew, kurtosis, autocorr, averagePairwise, annualizedMarketVol);
    }

    public static void main(String[] args) {
        Report report = run();
        System.out.printf(Locale.ROOT, "steps=%d%n", report.steps());
        System.out.printf(Locale.ROOT, "marketReturnMean=%.10f%n", report.mean());
        System.out.printf(Locale.ROOT, "marketReturnStd=%.10f%n", report.std());
        System.out.printf(Locale.ROOT, "marketReturnSkew=%.6f%n", report.skew());
        System.out.printf(Locale.ROOT, "marketReturnKurtosis=%.6f%n", report.kurtosis());
        System.out.printf(Locale.ROOT, "marketReturnAutocorrLag1=%.6f%n", report.autocorrLag1());
        System.out.printf(Locale.ROOT, "averagePairwiseCorrelation=%.6f%n", report.averagePairwiseCorrelation());
        System.out.printf(Locale.ROOT, "annualizedMarketVol=%.6f%n", report.annualizedMarketVol());
    }

    private static double mean(double[] values) {
        double sum = 0.0;
        for (double value : values) {
            sum += value;
        }
        return sum / values.length;
    }

    private static double std(double[] values, double mean) {
        double sum = 0.0;
        for (double value : values) {
            double d = value - mean;
            sum += d * d;
        }
        return Math.sqrt(sum / values.length);
    }

    private static double skew(double[] values, double mean, double std) {
        double sum = 0.0;
        for (double value : values) {
            sum += Math.pow((value - mean) / std, 3);
        }
        return sum / values.length;
    }

    private static double kurtosis(double[] values, double mean, double std) {
        double sum = 0.0;
        for (double value : values) {
            sum += Math.pow((value - mean) / std, 4);
        }
        return sum / values.length;
    }

    /** Pearson correlation between {@code a[offsetA ..]} and {@code b[offsetB ..]} over {@code length}. */
    private static double correlation(double[] a, int offsetA, double[] b, int offsetB, int length) {
        double meanA = 0.0;
        double meanB = 0.0;
        for (int i = 0; i < length; i++) {
            meanA += a[offsetA + i];
            meanB += b[offsetB + i];
        }
        meanA /= length;
        meanB /= length;
        double covariance = 0.0;
        double varianceA = 0.0;
        double varianceB = 0.0;
        for (int i = 0; i < length; i++) {
            double da = a[offsetA + i] - meanA;
            double db = b[offsetB + i] - meanB;
            covariance += da * db;
            varianceA += da * da;
            varianceB += db * db;
        }
        return covariance / Math.sqrt(varianceA * varianceB);
    }
}
