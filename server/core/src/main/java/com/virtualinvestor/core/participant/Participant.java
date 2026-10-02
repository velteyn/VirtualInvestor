package com.virtualinvestor.core.participant;

import com.virtualinvestor.core.book.IncomingOrder;
import com.virtualinvestor.core.event.GameEvent;
import java.util.List;

/**
 * A trader in a round — the human player, a rival bot, or an institution. The engine knows
 * only this interface, so a human command and a bot decision flow through exactly the same
 * path, and a human can later be added without changing the engine.
 */
public interface Participant {

    String id();

    ParticipantType type();

    String displayName();

    Account account();

    /** Route an order to the market through the shared gateway. */
    default List<GameEvent> placeOrder(TradeGateway gateway, IncomingOrder order, long tick) {
        return gateway.submit(this, order, tick);
    }

    /** Request cancellation of a working order. */
    default List<GameEvent> cancelOrder(TradeGateway gateway, String orderId, long tick) {
        return gateway.cancel(this, orderId, tick);
    }
}
