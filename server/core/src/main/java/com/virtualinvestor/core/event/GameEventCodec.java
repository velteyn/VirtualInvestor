package com.virtualinvestor.core.event;

import com.virtualinvestor.core.codec.RecordTextCodec;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Encodes and decodes {@link GameEvent}s by their simple type name, so a stored event log can
 * be replayed. The type registry is derived from the sealed interface's permitted subclasses,
 * so a newly added event type is picked up automatically.
 */
public final class GameEventCodec {

    private static final Map<String, Class<? extends GameEvent>> TYPES = buildRegistry();

    private GameEventCodec() {
    }

    public static String typeOf(GameEvent event) {
        return event.getClass().getSimpleName();
    }

    public static String encode(GameEvent event) {
        return RecordTextCodec.encode(event);
    }

    public static GameEvent decode(String type, String payload) {
        Class<? extends GameEvent> eventType = TYPES.get(type);
        if (eventType == null) {
            throw new IllegalArgumentException("unknown event type: " + type);
        }
        return RecordTextCodec.decode(payload, eventType);
    }

    private static Map<String, Class<? extends GameEvent>> buildRegistry() {
        Map<String, Class<? extends GameEvent>> registry = new LinkedHashMap<>();
        Class<?>[] permitted = GameEvent.class.getPermittedSubclasses();
        if (permitted != null) {
            for (Class<?> candidate : permitted) {
                if (GameEvent.class.isAssignableFrom(candidate)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends GameEvent> typed = (Class<? extends GameEvent>) candidate;
                    registry.put(typed.getSimpleName(), typed);
                }
            }
        }
        return Collections.unmodifiableMap(registry);
    }
}
