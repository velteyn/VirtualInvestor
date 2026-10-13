package com.virtualinvestor.edge.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.virtualinvestor.edge.EdgeApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = EdgeApplication.class)
@AutoConfigureMockMvc
class RoundControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.virtualinvestor.edge.game.GameService game;

    @Autowired
    private javax.sql.DataSource dataSource;

    private final java.util.List<String> createdRounds = new java.util.ArrayList<>();

    private void cleanJournal() throws Exception {
        // Round ids recycle across contexts sharing one database, and the writer
        // upserts by (round_id, seq): stale rows would shadow this test's events.
        // Clean before running as well as after.
        if (createdRounds.isEmpty()) {
            return;
        }
        try (java.sql.Connection connection = dataSource.getConnection();
                java.sql.PreparedStatement statement = connection
                        .prepareStatement("DELETE FROM events WHERE round_id = ANY (?)")) {
            statement.setArray(1, connection.createArrayOf("text", createdRounds.toArray()));
            statement.executeUpdate();
        }
    }

    @org.junit.jupiter.api.AfterEach
    void cleanJournalAfter() throws Exception {
        cleanJournal();
        createdRounds.clear();
    }

    @Test
    void createRoundReturnsAConfiguredRoundV1() throws Exception {
        mockMvc.perform(post("/api/rounds")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startingCapital\":100000,\"durationMinutes\":30,\"preset\":\"REALISTIC\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roundId").isNotEmpty())
                .andExpect(jsonPath("$.state").value("CREATED"))
                .andExpect(jsonPath("$.preset").value("REALISTIC"))
                .andExpect(jsonPath("$.durationTicks").value(18000))
                .andExpect(jsonPath("$.sessionToken").isNotEmpty());
    }

    @Test
    void invalidConfigIsRejected() throws Exception {
        mockMvc.perform(post("/api/rounds")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startingCapital\":1,\"durationMinutes\":30,\"preset\":\"REALISTIC\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void symbolSearchReturnsMatches() throws Exception {
        mockMvc.perform(post("/api/symbols/search")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"NVDO\",\"limit\":5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("NVDO"))
                .andExpect(jsonPath("$[0].assetClass").value("Equity"));
    }

    @Test
    void symbolChainReturnsFutureFirst() throws Exception {
        mockMvc.perform(post("/api/symbols/chain")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"underlying\":\"NVDO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].ticker").value("NVDO-F"))
                .andExpect(jsonPath("$[0].assetClass").value("Future"));
    }

    @Test
    void unsupportedVersionIsRejected() throws Exception {
        mockMvc.perform(post("/api/rounds")
                        .header("X-API-Version", "99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startingCapital\":100000,\"durationMinutes\":30,\"preset\":\"REALISTIC\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void missingVersionIsRejected() throws Exception {
        mockMvc.perform(post("/api/rounds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startingCapital\":100000,\"durationMinutes\":30,\"preset\":\"REALISTIC\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fillsReturnsOnlyTheRequestersFills() throws Exception {
        com.virtualinvestor.core.game.LaunchConfig config =
                new com.virtualinvestor.core.game.LaunchConfig(100_000.0, 60L,
                        com.virtualinvestor.core.rules.RealismPreset.REALISTIC);
        String roundId =
                game.createRound(config, java.util.List.<String>of(), false, 60_000L).roundId();
        createdRounds.add(roundId);
        cleanJournal();
        com.virtualinvestor.runtime.RunningRound live = game.runningRound(roundId).orElseThrow();
        for (int i = 0; i < 4; i++) {
            live.step();
        }
        // Seeds differ per round (shared counter), and a halted book rejects takes:
        // retry a few ticks until the buy fills, then assert on that order id.
        String filledId = null;
        for (int attempt = 0; attempt < 5 && filledId == null; attempt++) {
            String orderId = "fill-" + attempt;
            live.enqueue(new com.virtualinvestor.core.command.Command.PlaceOrder(orderId, "human", "NVDO",
                    com.virtualinvestor.core.domain.OrderSide.BUY,
                    com.virtualinvestor.core.domain.OrderType.MARKET,
                    5L, 0.0, 0.0, 0L));
            java.util.List<com.virtualinvestor.core.event.GameEvent> events = live.step();
            boolean filled = events.stream()
                    .filter(com.virtualinvestor.core.event.GameEvent.OrderReport.class::isInstance)
                    .map(com.virtualinvestor.core.event.GameEvent.OrderReport.class::cast)
                    .anyMatch(r -> r.orderId().equals(orderId)
                            && r.event() == com.virtualinvestor.core.domain.OrderEventKind.FILL);
            if (filled) {
                filledId = orderId;
            }
        }
        assertTrue(filledId != null, "a market take should fill within a few ticks");
        final String filledOrder = filledId;

        mockMvc.perform(post("/api/rounds/fills")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\",\"participantId\":\"human\"}"))
                .andExpect(status().isOk())
                // Exactly the human's fill: bot fills from the same ticks are excluded.
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].orderId").value(filledOrder))
                .andExpect(jsonPath("$[0].filledQuantity").value(5))
                .andExpect(jsonPath("$[0].fee").exists())
                .andExpect(jsonPath("$[0].tick").exists())
                .andExpect(jsonPath("$[0].side").value("BUY"));

        mockMvc.perform(post("/api/rounds/fills")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"ghost\",\"participantId\":\"human\"}"))
                .andExpect(status().isNotFound());
    }
}
