package com.virtualinvestor.core.participant;

import com.virtualinvestor.core.book.IncomingOrder;
import com.virtualinvestor.core.engine.InstrumentEngine;
import com.virtualinvestor.core.event.GameEvent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The single entry point through which every participant — human or virtual — reaches the
 * market. It routes an order to the instrument's engine and refuses an order whose named
 * participant does not match the sender, so identity cannot be spoofed.
 */
public final class TradeGateway {

    private final Map<String, InstrumentEngine> engines = new LinkedHashMap<>();

    public void register(InstrumentEngine engine) {
        engines.put(engine.symbol(), engine);
    }

    public InstrumentEngine engine(String symbol) {
        InstrumentEngine engine = engines.get(symbol);
        if (engine == null) {
            throw new IllegalArgumentException("no engine registered for " + symbol);
        }
        return engine;
    }

    public List<GameEvent> submit(Participant participant, IncomingOrder order, long tick) {
        Objects.requireNonNull(participant, "participant");
        Objects.requireNonNull(order, "order");
        if (!participant.id().equals(order.participantId())) {
            throw new IllegalArgumentException(
                    "order participant " + order.participantId() + " does not match sender " + participant.id());
        }
        return engine(order.symbol()).submit(order, tick);
    }

    public List<GameEvent> cancel(Participant participant, String orderId, long tick) {
        Objects.requireNonNull(participant, "participant");
        for (InstrumentEngine engine : engines.values()) {
            List<GameEvent> events = engine.cancel(orderId, tick);
            if (!events.isEmpty()) {
                return events;
            }
        }
        return List.of();
    }
}
