package com.virtualinvestor.persist.projection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.event.GameEvent;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ProjectionTest {

    private static Stream<GameEvent> log() {
        return Stream.of(
                new GameEvent.Tick(1L, 100L),
                new GameEvent.TradePrinted("ACME", 100.0, 10L, OrderSide.BUY, 2L),
                new GameEvent.TradePrinted("ACME", 100.5, 5L, OrderSide.SELL, 3L),
                new GameEvent.TradePrinted("NVDO", 640.0, 2L, OrderSide.BUY, 4L),
                new GameEvent.LiquidationExecuted("h1", "ACME", 100L, 98.0, 5L),
                new GameEvent.RoundSettled("round-1", 6L));
    }

    @Test
    void portfolioProjectionFoldsTradePrints() {
        PortfolioProjection.Snapshot snapshot = ProjectionBuilder.rebuild(new PortfolioProjection(), log());

        assertEquals(15L, snapshot.filledQuantity().get("ACME"));
        assertEquals(2L, snapshot.prints().get("ACME"));
        assertEquals(2L, snapshot.filledQuantity().get("NVDO"));
    }

    @Test
    void leaderboardProjectionCapturesSettlementAndLiquidations() {
        LeaderboardProjection.Snapshot snapshot = ProjectionBuilder.rebuild(new LeaderboardProjection(), log());

        assertTrue(snapshot.settled());
        assertEquals(6L, snapshot.settledTick());
        assertEquals(1L, snapshot.liquidationsByParticipant().get("h1"));
    }

    @Test
    void projectionsAreDisposableAndRebuildable() {
        // The same event stream yields the same read model every time it is folded.
        assertEquals(
                ProjectionBuilder.rebuild(new PortfolioProjection(), log()).filledQuantity(),
                ProjectionBuilder.rebuild(new PortfolioProjection(), log()).filledQuantity());
        assertFalse(ProjectionBuilder.rebuild(new LeaderboardProjection(), Stream.<GameEvent>empty()).settled());
    }
}
