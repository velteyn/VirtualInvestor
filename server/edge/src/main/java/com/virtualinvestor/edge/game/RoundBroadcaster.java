package com.virtualinvestor.edge.game;

import com.virtualinvestor.core.book.DepthLevel;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.engine.InstrumentEngine;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.edge.api.WireMapper;
import com.virtualinvestor.edge.wire.WireMessage;
import com.virtualinvestor.edge.ws.MarketBroadcaster;
import com.virtualinvestor.runtime.EventPublisher;
import java.util.List;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/**
 * The edge binding from the world's event stream to the WebSocket broadcaster (design D6/D7).
 * It maps each core event to a wire payload — trade prints, news, order status, and a coalesced
 * depth snapshot — and hands them to {@link MarketBroadcaster}. The runtime never depends on the
 * edge; this class depends on the runtime only through {@link EventPublisher}.
 */
@Component
public class RoundBroadcaster implements EventPublisher {

    private static final int DEPTH_LEVELS = 10;

    private final MarketBroadcaster broadcaster;
    private Supplier<List<InstrumentEngine>> engines = List::of;

    public RoundBroadcaster(MarketBroadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }

    /** Supply the running round's engines so depth snapshots can be published. */
    public void bindEngines(Supplier<List<InstrumentEngine>> engines) {
        this.engines = engines;
    }

    @Override
    public void publish(List<GameEvent> events, long sequence) {
        for (GameEvent event : events) {
            if (event instanceof GameEvent.TradePrinted trade) {
                broadcaster.broadcast(new WireMessage.TradeDto(
                        trade.symbol(), trade.price(), trade.quantity(), trade.aggressorSide().name(), trade.tick()));
                publishDepth(trade.symbol(), trade.tick());
            } else if (event instanceof GameEvent.NewsReleased news) {
                broadcaster.broadcast(new WireMessage.NewsDto(
                        news.newsId(), news.source(), news.scope().name(), news.symbol(), news.sector(),
                        news.headline(), news.body(), news.magnitude(), news.durationTicks(),
                        news.stage().name(), news.tick()));
            } else if (event instanceof GameEvent.OrderFilled filled) {
                broadcaster.broadcast(new WireMessage.OrderDto(
                        filled.orderId(), "", filled.symbol(), "", "", filled.filledQuantity(),
                        filled.price(), 0.0, "FILLED", filled.filledQuantity(), filled.tick()));
            } else if (event instanceof GameEvent.OrderPartiallyFilled partial) {
                broadcaster.broadcast(new WireMessage.OrderDto(
                        partial.orderId(), "", partial.symbol(), "", "", partial.filledQuantity(),
                        partial.price(), 0.0, "PARTIAL", partial.filledQuantity(), partial.tick()));
            }
        }
    }

    private void publishDepth(String symbol, long tick) {
        for (InstrumentEngine engine : engines.get()) {
            if (!engine.symbol().equals(symbol)) {
                continue;
            }
            List<DepthLevel> bids = engine.book().depth(OrderSide.BUY, DEPTH_LEVELS);
            List<DepthLevel> asks = engine.book().depth(OrderSide.SELL, DEPTH_LEVELS);
            broadcaster.depth(WireMapper.depth(symbol, bids, asks, engine.lastPrice(), tick));
            return;
        }
    }
}
