package com.virtualinvestor.edge.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.virtualinvestor.core.command.Command;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;
import com.virtualinvestor.edge.api.WireMapper;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class WireCommandTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void orderCommandsRoundTripThroughJson() throws Exception {
        WireMessage.Payload[] commands = {
                new WireMessage.PlaceOrderCommand("t1", "NVDO", "BUY", "LIMIT", 100L, 640.0, 0.0),
                new WireMessage.ModifyOrderCommand("t2", "o1", 645.0, 0.0),
                new WireMessage.CancelOrderCommand("t3", "o1")};

        for (WireMessage.Payload command : commands) {
            String json = mapper.writeValueAsString(command);
            WireMessage.Payload decoded = mapper.readValue(json, WireMessage.Payload.class);
            assertEquals(command, decoded, command.getClass().getSimpleName());
        }
    }

    @Test
    void placeCommandMapsToCoreCommand() {
        WireMessage.PlaceOrderCommand wire =
                new WireMessage.PlaceOrderCommand("t1", "NVDO", "buy", "limit", 100L, 640.5, 0.0);

        Command command = WireMapper.command("human", wire, 7L);

        Command.PlaceOrder place = (Command.PlaceOrder) command;
        assertEquals("t1", place.commandId());
        assertEquals("human", place.participantId());
        assertEquals("NVDO", place.symbol());
        assertEquals(OrderSide.BUY, place.side());
        assertEquals(OrderType.LIMIT, place.type());
        assertEquals(100L, place.quantity());
        assertEquals(640.5, place.price(), 0.0);
        assertEquals(7L, place.tick());
    }

    @Test
    void modifyAndCancelMapToCoreCommands() {
        Command modify = WireMapper.command("human",
                new WireMessage.ModifyOrderCommand("t2", "o1", 645.0, 0.0), 8L);
        assertEquals("o1", ((Command.ModifyOrder) modify).orderId());
        assertEquals(645.0, ((Command.ModifyOrder) modify).price(), 0.0);

        Command cancel = WireMapper.command("human", new WireMessage.CancelOrderCommand("t3", "o1"), 9L);
        assertEquals("o1", ((Command.CancelOrder) cancel).orderId());
    }

    @Test
    void brokerAliasesForStopTypesAreAccepted() {
        assertEquals(OrderType.STOP, WireMapper.type("SL"));
        assertEquals(OrderType.STOP_LIMIT, WireMapper.type("SL-M"));
        assertEquals(OrderType.MARKET, WireMapper.type("MKT"));
    }

    @Test
    void missingTokenFallsBackToADeterministicId() {
        Command command = WireMapper.command("human",
                new WireMessage.PlaceOrderCommand(null, "NVDO", "BUY", "MARKET", 10L, 0.0, 0.0), 5L);
        assertEquals("human-place-5", command.commandId());
    }

    @Test
    void unknownSideOrTypeRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> WireMapper.command("human", new WireMessage.PlaceOrderCommand("t", "X", "SIDEWAYS", "LIMIT", 1L, 1.0, 0.0), 1L));
        assertThrows(IllegalArgumentException.class,
                () -> WireMapper.command("human", new WireMessage.PlaceOrderCommand("t", "X", "BUY", "WEIRD", 1L, 1.0, 0.0), 1L));
    }
}
