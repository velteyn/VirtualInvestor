package com.virtualinvestor.persist.snapshot;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.event.GameEventCodec;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Encodes a world snapshot as the ordered list of events up to a sequence number. Because the
 * world is a pure function of the seed and the ordered event log, "state at seq N" is exactly
 * "the first N events"; recovery replays events after N. This keeps snapshots compact, codec-
 * consistent with the log, and exact.
 */
public final class WorldStateCodec {

    private WorldStateCodec() {
    }

    public static String encode(List<GameEvent> events) {
        StringJoiner joiner = new StringJoiner("\n");
        for (GameEvent event : events) {
            joiner.add(GameEventCodec.typeOf(event) + "\t" + GameEventCodec.encode(event).replace("\n", "\\n"));
        }
        return joiner.toString();
    }

    public static List<GameEvent> decode(String state) {
        List<GameEvent> events = new ArrayList<>();
        if (state == null || state.isEmpty()) {
            return List.copyOf(events);
        }
        for (String line : state.split("\n", -1)) {
            if (line.isEmpty()) {
                continue;
            }
            int tab = line.indexOf('\t');
            String type = line.substring(0, tab);
            String payload = line.substring(tab + 1).replace("\\n", "\n");
            events.add(GameEventCodec.decode(type, payload));
        }
        return List.copyOf(events);
    }
}
