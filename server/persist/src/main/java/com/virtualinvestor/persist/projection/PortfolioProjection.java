package com.virtualinvestor.persist.projection;

import com.virtualinvestor.core.event.GameEvent;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Rebuilds per-symbol filled quantity and traded volume from trade prints. In the final system
 * this folds order/fill events into participant positions; today it demonstrates the rebuild
 * contract against the event set the buyer can drive.
 */
public final class PortfolioProjection implements Projection<PortfolioProjection.Snapshot> {

    /** Filled quantity and number of prints per symbol. */
    public record Snapshot(Map<String, Long> filledQuantity, Map<String, Long> prints) {
        public Snapshot {
            filledQuantity = Collections.unmodifiableMap(new LinkedHashMap<>(filledQuantity));
            prints = Collections.unmodifiableMap(new LinkedHashMap<>(prints));
        }
    }

    private final Map<String, Long> filledQuantity = new LinkedHashMap<>();
    private final Map<String, Long> prints = new LinkedHashMap<>();

    @Override
    public void apply(long seq, GameEvent event) {
        if (event instanceof GameEvent.TradePrinted trade) {
            filledQuantity.merge(trade.symbol(), trade.quantity(), Long::sum);
            prints.merge(trade.symbol(), 1L, Long::sum);
        }
    }

    @Override
    public Snapshot result() {
        return new Snapshot(filledQuantity, prints);
    }
}
