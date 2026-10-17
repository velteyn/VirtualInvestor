package com.virtualinvestor.edge.game;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.runtime.EventPublisher;
import java.util.List;

/**
 * Round attribution for the shared edge publisher (newsroom-narrator 2.3):
 * tags each event batch with its round so the newsroom can file articles
 * per round. Everything delegates to the shared broadcaster unchanged.
 */
final class RoundEvents implements EventPublisher {

    private final String roundId;
    private final EventPublisher delegate;
    private final NewsroomService newsroom;

    RoundEvents(String roundId, EventPublisher delegate, NewsroomService newsroom) {
        this.roundId = roundId;
        this.delegate = delegate;
        this.newsroom = newsroom;
    }

    @Override
    public void publish(List<GameEvent> events, long sequence) {
        if (newsroom != null) {
            for (GameEvent event : events) {
                if (event instanceof GameEvent.Tick tick) {
                    newsroom.noteTick(roundId, tick.tick());
                } else if (event instanceof GameEvent.TradePrinted trade) {
                    newsroom.noteTrade(roundId, trade.symbol(), trade.price());
                } else if (event instanceof GameEvent.NewsReleased news) {
                    newsroom.accept(roundId, news);
                } else if (event instanceof GameEvent.OrderReport report
                        && report.status() == com.virtualinvestor.core.domain.OrderStatus.REJECTED
                        && report.reason() != null && report.reason().contains("halt")) {
                    newsroom.noteHalt(roundId, report.symbol());
                } else if (event instanceof GameEvent.RoundSettled settled) {
                    newsroom.recap(roundId, settled.tick());
                    newsroom.forget(roundId);
                }
            }
        }
        delegate.publish(events, sequence);
    }
}
