package com.virtualinvestor.edge.ws;

import com.virtualinvestor.edge.wire.WireMessage;
import tools.jackson.databind.ObjectMapper;

/** Shared JSON codec for the wire payloads, so REST-free paths (WebSocket) can serialize too. */
public final class WireCoder {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private WireCoder() {
    }

    public static String toJson(WireMessage.Payload payload) {
        return MAPPER.writeValueAsString(payload);
    }

    public static WireMessage.Payload fromJson(String json) {
        return MAPPER.readValue(json, WireMessage.Payload.class);
    }
}
