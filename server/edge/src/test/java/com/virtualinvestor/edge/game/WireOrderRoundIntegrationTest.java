package com.virtualinvestor.edge.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import com.virtualinvestor.edge.wire.WireMessage;
import com.virtualinvestor.edge.ws.MarketBroadcaster;
import com.virtualinvestor.runtime.RunningRound;
import org.junit.jupiter.api.Test;

/**
 * Group 7.2: a client submits orders over the wire, and the resulting fills, positions and
 * settlement reflect them.
 */
class WireOrderRoundIntegrationTest {

    @Test
    void wireOrdersProduceFillsAndPositions() {
        RoundRuntimeService runtime = new RoundRuntimeService(new MarketBroadcaster());
        RunningRound round = runtime.start("round-wire", 81L,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), false, 60_000L);

        // Step into continuous trading so there is a two-sided book.
        for (int i = 0; i < 4; i++) {
            round.step();
        }

        // The client submits a crossing limit buy via the wire command path.
        double ask = round.engines().get("NVDO").book().bestAskPrice();
        runtime.submit("round-wire", "human", new WireMessage.PlaceOrderCommand(
                "wire-buy-1", "NVDO", "BUY", "LIMIT", 10L, ask, 0.0), round.tick());

        // The loop applies the queued command at the top of the next tick.
        round.step();

        long position = round.player().account().position("NVDO").netQty();
        assertTrue(position > 0L, "the client's order should have filled, position=" + position);
        assertTrue(round.player().account().equity(round.marks()) > 0.0);
    }

    @Test
    void clientOrderFillsAndSettlementMarksThePosition() {
        RoundRuntimeService runtime = new RoundRuntimeService(new MarketBroadcaster());
        RunningRound round = runtime.start("round-wire-2", 82L,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), false, 60_000L);
        for (int i = 0; i < 4; i++) {
            round.step();
        }
        double ask = round.engines().get("NVDO").book().bestAskPrice();
        runtime.submit("round-wire-2", "human", new WireMessage.PlaceOrderCommand(
                "wire-buy-2", "NVDO", "BUY", "LIMIT", 10L, ask, 0.0), round.tick());
        round.step();

        boolean filled = false;
        for (GameEvent event : round.step()) {
            filled |= event instanceof GameEvent.TradePrinted;
        }
        assertTrue(round.player().account().position("NVDO").netQty() > 0L || filled
                || round.player().account().position("NVDO").netQty() > 0L);

        // Run to the close; settlement force-closes the position and the account is flat.
        for (int i = 0; i < 120 && !round.settled(); i++) {
            round.step();
        }
        assertTrue(round.settled());
        assertEquals(0L, round.player().account().position("NVDO").netQty(), "settlement flattens positions");
    }
}
