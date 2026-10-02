package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Group 3.3: a repeated command with the same client token is applied once. */
class OrderIdempotencyTest {

    private static final long MINUTE_MILLIS = 60_000L;

    @Test
    void repeatedCommandCreatesOneOrder() {
        RunningRound round = new RunningRound("round-idem", 31L,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), MINUTE_MILLIS);
        round.start();
        for (int i = 0; i < 4; i++) {
            round.step();
        }
        double price = Math.round(round.fundamentals().fundamental("NVDO") * 0.5 * 100.0) / 100.0;
        Command place = new Command.PlaceOrder(
                "token-1", "human", "NVDO", OrderSide.BUY, OrderType.LIMIT, 10L, price, 0.0, 0L);

        round.enqueue(place);
        round.enqueue(place);
        List<GameEvent> events = round.step();

        long accepted = events.stream()
                .filter(e -> e instanceof GameEvent.OrderAccepted a && a.orderId().equals("token-1"))
                .count();
        assertEquals(1, accepted, "the duplicate command must be ignored");
    }
}
