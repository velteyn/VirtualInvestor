package com.virtualinvestor.edge.wire;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.virtualinvestor.edge.wire.WireMessage.AccountDto;
import com.virtualinvestor.edge.wire.WireMessage.BarDto;
import com.virtualinvestor.edge.wire.WireMessage.DepthDto;
import com.virtualinvestor.edge.wire.WireMessage.ErrorMessage;
import com.virtualinvestor.edge.wire.WireMessage.LeaderboardEntryDto;
import com.virtualinvestor.edge.wire.WireMessage.NewsDto;
import com.virtualinvestor.edge.wire.WireMessage.OrderDto;
import com.virtualinvestor.edge.wire.WireMessage.Payload;
import com.virtualinvestor.edge.wire.WireMessage.PositionDto;
import com.virtualinvestor.edge.wire.WireMessage.RoundDto;
import com.virtualinvestor.edge.wire.WireMessage.ServerHello;
import com.virtualinvestor.edge.wire.WireMessage.SymbolDto;
import com.virtualinvestor.edge.wire.WireMessage.TradeDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class WireCodecTest {

    private final ObjectMapper mapper = new ObjectMapper();

    private List<Payload> samples() {
        return List.of(
                new ServerHello(),
                new ErrorMessage("bad_request", "nope"),
                new BarDto("ACME", "1m", 1_700_000_000L, 100.0, 101.0, 99.0, 100.5, 5000L),
                new DepthDto("ACME", List.of(new DepthDto.Level(99.9, 300L)),
                        List.of(new DepthDto.Level(100.1, 250L)), 100.0, 7L),
                new TradeDto("ACME", 100.0, 50L, "BUY", 8L),
                new OrderDto("o1", "h1", "ACME", "BUY", "LIMIT", 100L, 99.5, 0.0, "WORKING", 0L, null, 8L),
                new PositionDto("h1", "ACME", 100L, 99.5, 12.5),
                new AccountDto("h1", 95_000.0, 0.0, 2.5),
                new NewsDto("n1", "THE_WIRE", "MACRO", null, null, "Data beats", "body", 0.01, 300L, "RUMOR", 9L),
                new LeaderboardEntryDto(1, "rival-1", "MARK", 120_000.0, 100_000.0, 0.2),
                new SymbolDto("ACME", "Acme Corp", "Tech", "Equity"),
                new RoundDto("r1", 42L, 100_000.0, 30L, "REALISTIC", "RUNNING", "token"));
    }

    @Test
    void everyPayloadRoundTripsThroughJsonWithATypeDiscriminator() throws Exception {
        for (Payload payload : samples()) {
            String json = mapper.writeValueAsString(payload);
            JsonNode node = mapper.readTree(json);
            assertTrue(node.hasNonNull("type"), () -> "missing type discriminator: " + json);
            Payload decoded = mapper.readValue(json, Payload.class);
            assertEquals(payload, decoded, payload.getClass().getSimpleName());
        }
    }

    @Test
    void helloCarriesTheProtocolVersion() throws Exception {
        Payload decoded = mapper.readValue(mapper.writeValueAsString(new ServerHello()), Payload.class);
        ServerHello hello = assertInstanceOf(ServerHello.class, decoded);
        assertEquals(WireMessage.PROTOCOL_VERSION, hello.protocolVersion());
    }

    @Test
    void sealedHierarchyIsClosedToKnownTypes() {
        assertInstanceOf(Payload.class, new BarDto("X", "1m", 0L, 0, 0, 0, 0, 0L));
    }
}
