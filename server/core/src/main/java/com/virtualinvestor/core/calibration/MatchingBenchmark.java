package com.virtualinvestor.core.calibration;

import com.virtualinvestor.core.book.IncomingOrder;
import com.virtualinvestor.core.book.RestingOrder;
import com.virtualinvestor.core.clock.TimeSource;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.engine.InstrumentEngine;
import java.util.Locale;

/**
 * Rough matching-throughput benchmark (task 4.5). Uses the injected {@link TimeSource} rather
 * than a direct wall-clock call so it stays within the core determinism rules; millisecond
 * resolution is enough because the run is several million operations.
 *
 * <pre>{@code
 * cd server && mvn -q -pl core compile && \
 *   java -cp core/target/classes com.virtualinvestor.core.calibration.MatchingBenchmark
 * }</pre>
 */
public final class MatchingBenchmark {

    private static final int INSTRUMENTS = 20;
    private static final int WARMUP_OPS = 200_000;
    private static final int MEASURED_OPS = 5_000_000;
    private static final long BOOK_DEPTH = 1_000_000_000L;
    private static final long ORDER_QTY = 100L;
    private static final double BID = 99.98;
    private static final double ASK = 100.02;

    private MatchingBenchmark() {
    }

    public static void main(String[] args) {
        InstrumentEngine[] engines = new InstrumentEngine[INSTRUMENTS];
        for (int i = 0; i < INSTRUMENTS; i++) {
            String symbol = "BENCH" + i;
            InstrumentEngine engine = new InstrumentEngine(symbol, 100.0);
            engine.rest(new RestingOrder("seed-bid-" + i, "seed", symbol, OrderSide.BUY, BID, BOOK_DEPTH));
            engine.rest(new RestingOrder("seed-ask-" + i, "seed", symbol, OrderSide.SELL, ASK, BOOK_DEPTH));
            engines[i] = engine;
        }

        run(engines, 1_000_000L, WARMUP_OPS, false);

        long startId = 10_000_000L;
        long start = TimeSource.system().nowMillis();
        run(engines, startId, MEASURED_OPS, true);
        long elapsedMillis = Math.max(1L, TimeSource.system().nowMillis() - start);

        double opsPerSecond = MEASURED_OPS * 1000.0 / elapsedMillis;
        System.out.printf(Locale.ROOT, "instruments=%d%n", INSTRUMENTS);
        System.out.printf(Locale.ROOT, "measuredOps=%d%n", MEASURED_OPS);
        System.out.printf(Locale.ROOT, "elapsedMillis=%d%n", elapsedMillis);
        System.out.printf(Locale.ROOT, "matchingOpsPerSecond=%.0f%n", opsPerSecond);
        System.out.printf(Locale.ROOT, "matchingOpsPerSecondPerInstrument=%.0f%n", opsPerSecond / INSTRUMENTS);
    }

    private static void run(InstrumentEngine[] engines, long idSeed, int ops, boolean fill) {
        for (int i = 0; i < ops; i++) {
            InstrumentEngine engine = engines[i % engines.length];
            boolean buy = (i & 1) == 0;
            IncomingOrder order = new IncomingOrder(
                    "bench-" + (idSeed + i),
                    "bench",
                    engine.symbol(),
                    buy ? OrderSide.BUY : OrderSide.SELL,
                    OrderType.LIMIT,
                    ORDER_QTY,
                    buy ? ASK : BID);
            engine.submit(order, i);
        }
    }
}
