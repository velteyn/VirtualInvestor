package com.virtualinvestor.core.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class GameEventCodecTest {

    @Test
    void encodesAndDecodesByTypeName() {
        GameEvent event = new GameEvent.TradePrinted("ACME", 100.5, 25L,
                com.virtualinvestor.core.domain.OrderSide.BUY, 42L);

        String type = GameEventCodec.typeOf(event);
        String payload = GameEventCodec.encode(event);

        assertEquals("TradePrinted", type);
        assertEquals(event, GameEventCodec.decode(type, payload));
    }

    @Test
    void unknownTypeRejected() {
        assertThrows(IllegalArgumentException.class, () -> GameEventCodec.decode("NotAnEvent", "x"));
    }

    @Test
    void settlementEventsRoundTrip() {
        GameEvent futures = new GameEvent.FuturesSettled("h1", "NVDO-F", 5L, 650.0, 60L);
        assertEquals(futures, GameEventCodec.decode(GameEventCodec.typeOf(futures), GameEventCodec.encode(futures)));

        GameEvent option = new GameEvent.OptionSettled("h1", "NVDO-C640", 5L, 10.0, 60L);
        assertEquals(option, GameEventCodec.decode(GameEventCodec.typeOf(option), GameEventCodec.encode(option)));
    }
}
