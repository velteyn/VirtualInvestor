package com.virtualinvestor.runtime;

import com.virtualinvestor.core.event.GameEvent;
import java.util.List;

/**
 * Receives the world's output stream for fan-out to clients (design D6). The runtime is
 * framework-free, so the edge supplies the implementation that maps events to wire payloads and
 * hands them to the WebSocket broadcaster. A no-op implementation lets the runtime run headless.
 */
public interface EventPublisher {

    void publish(List<GameEvent> events, long sequence);

    static EventPublisher noop() {
        return (events, sequence) -> {
        };
    }
}
