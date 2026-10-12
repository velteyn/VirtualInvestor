package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderEventKind;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.game.Settlement;
import com.virtualinvestor.core.participant.HumanParticipant;
import com.virtualinvestor.core.rules.RealismPreset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Group 5.3: notional sizing caps and the joint loss bound for levered books. */
class RiskLimitsDerivativesTest {

    private static final long MINUTE_MILLIS = 60_000L;

    private static RunningRound continuousRound(long seed) {
        RunningRound round = new RunningRound("round-risk", seed,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), MINUTE_MILLIS);
        round.start();
        for (int i = 0; i < 4; i++) {
            round.step();
        }
        return round;
    }

    private static String rejectionReason(List<GameEvent> events, String orderId) {
        for (GameEvent event : events) {
            if (event instanceof GameEvent.OrderReport rejected
                    && rejected.event() == OrderEventKind.REJECTED
                    && rejected.orderId().equals(orderId)) {
                return rejected.reason();
            }
        }
        return null;
    }

    @Test
    void oversizedFuturesOrderRefused() {
        RunningRound round = continuousRound(51L);
        double last = round.marks().get("NVDO-F");
        long tooMany = (long) (100_000.0 / last) + 100L;

        round.enqueue(new Command.PlaceOrder("r1", "human", "NVDO-F",
                OrderSide.BUY, OrderType.LIMIT, tooMany, last, 0.0, 0L));
        String reason = rejectionReason(round.step(), "r1");
        assertTrue(reason != null && reason.contains("exceeds equity"),
                "expected a sizing rejection, got: " + reason);
    }

    @Test
    void shortPutBlockedWhenMarginWouldNotCover() {
        RunningRound round = continuousRound(52L);
        // Strike 700 short: 0.5 * 700 * 1000 = 350k margin vs ~100k buying power.
        round.enqueue(new Command.PlaceOrder("r2", "human", "NVDO-P700",
                OrderSide.SELL, OrderType.LIMIT, 1000L, 5.0, 0.0, 0L));
        String reason = rejectionReason(round.step(), "r2");
        assertTrue(reason != null && reason.contains("margin"),
                "expected a margin rejection, got: " + reason);
    }

    @Test
    void worstCaseLeveredLossStaysBoundedByCapital() {
        HumanParticipant trader = new HumanParticipant("h1", "You", 100_000.0);
        // Max-size long future, then the underlying goes to zero.
        trader.account().applyFuturesFill("NVDO-F", OrderSide.BUY, 156L, 640.0, 128.0);

        Map<String, Double> closing = Map.of("NVDO", 0.0);
        Settlement.settle(List.of(trader), closing,
                new LinkedHashMap<>(Map.of("h1", 100_000.0)), 999L, "round-worst");

        assertTrue(trader.account().positions().isEmpty());
        assertTrue(trader.account().equity(closing) >= -100_000.0,
                "loss bounded by starting capital, got equity " + trader.account().equity(closing));
    }

    @Test
    void smallFuturesOrderPassesSizing() {
        RunningRound round = continuousRound(53L);
        double last = round.marks().get("NVDO-F");

        round.enqueue(new Command.PlaceOrder("r3", "human", "NVDO-F",
                OrderSide.BUY, OrderType.LIMIT, 5L, last, 0.0, 0L));
        round.step();
        round.enqueue(new Command.PlaceOrder("r4", "human", "NVDO-F",
                OrderSide.SELL, OrderType.LIMIT, 5L, last, 0.0, 0L));
        List<GameEvent> events = round.step();

        assertTrue(events.stream().noneMatch(e -> e instanceof GameEvent.OrderReport rejected
                && (rejected.orderId().equals("r3") || rejected.orderId().equals("r4"))
                && rejected.event() == OrderEventKind.REJECTED));
        assertTrue(round.participant("human").account().position("NVDO-F").isFlat());
        assertEquals(0.0, round.participant("human").account().lockedMargin(), 1e-9);
    }
}
