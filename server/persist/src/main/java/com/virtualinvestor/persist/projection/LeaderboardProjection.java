package com.virtualinvestor.persist.projection;

import com.virtualinvestor.core.event.GameEvent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rebuilds final round standings from settlement events. Every {@code LiquidationExecuted} is
 * ignored here (it is a position close, not a rank); {@code RoundSettled} marks the round
 * complete, and the ranking itself is supplied by the game layer. What this projection proves
 * is that standings can be reconstructed from the log.
 */
public final class LeaderboardProjection implements Projection<LeaderboardProjection.Snapshot> {

    public record Snapshot(boolean settled, long settledTick, Map<String, Long> liquidationsByParticipant) {
        public Snapshot {
            liquidationsByParticipant =
                    Collections.unmodifiableMap(new LinkedHashMap<>(liquidationsByParticipant));
        }
    }

    private boolean settled;
    private long settledTick;
    private final Map<String, Long> liquidations = new LinkedHashMap<>();

    @Override
    public void apply(long seq, GameEvent event) {
        if (event instanceof GameEvent.RoundSettled settledEvent) {
            this.settled = true;
            this.settledTick = settledEvent.tick();
        } else if (event instanceof GameEvent.LiquidationExecuted liquidation) {
            liquidations.merge(liquidation.participantId(), 1L, Long::sum);
        }
    }

    @Override
    public Snapshot result() {
        return new Snapshot(settled, settledTick, liquidations);
    }
}
