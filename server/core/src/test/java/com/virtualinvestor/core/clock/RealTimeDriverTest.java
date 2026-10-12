package com.virtualinvestor.core.clock;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RealTimeDriverTest {

    @Test
    void aThrowingTickDoesNotKillTheDriver() throws Exception {
        WorldClock clock = new WorldClock(10L, System::currentTimeMillis);
        AtomicInteger runs = new AtomicInteger();
        RealTimeDriver driver = new RealTimeDriver(clock, () -> {
            if (runs.incrementAndGet() == 1) {
                throw new IllegalStateException("boom");
            }
        });
        try {
            driver.start();
            long deadline = System.currentTimeMillis() + 5_000L;
            while (runs.get() < 3 && System.currentTimeMillis() < deadline) {
                Thread.sleep(10L);
            }
            assertTrue(runs.get() >= 3, "ticks kept running after the failure, runs=" + runs.get());
        } finally {
            driver.close();
        }
    }
}
