package com.virtualinvestor.edge.api;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.virtualinvestor.edge.EdgeApplication;
import com.virtualinvestor.edge.game.GameService;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Game-lobby 2.1/2.2/2.3 over HTTP: listing, room create/join/master-start with
 * both refusals, and unstarted-room expiry.
 */
@SpringBootTest(classes = EdgeApplication.class)
@AutoConfigureMockMvc
class RoomControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GameService game;

    @Autowired
    private javax.sql.DataSource dataSource;

    private final List<String> createdRounds = new ArrayList<>();

    @org.junit.jupiter.api.AfterEach
    void cleanJournalAfter() throws Exception {
        if (!createdRounds.isEmpty()) {
            try (java.sql.Connection connection = dataSource.getConnection();
                    java.sql.PreparedStatement statement = connection
                            .prepareStatement("DELETE FROM events WHERE round_id = ANY (?)")) {
                statement.setArray(1, connection.createArrayOf("text", createdRounds.toArray()));
                statement.executeUpdate();
            }
            createdRounds.clear();
        }
    }

    private String playerToken(String name) throws Exception {
        String username = name + "-" + UUID.randomUUID().toString().substring(0, 8);
        String playerId = game.players().store().register(username, "secret-1", "meridian").playerId();
        game.players().store().approve(playerId);
        return game.players().login(username, "secret-1").sessionToken();
    }

    private String createRoom(String token) throws Exception {
        MvcResult created = mockMvc.perform(post("/api/rooms")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startingCapital\":100000,\"durationMinutes\":30,\"preset\":\"REALISTIC\","
                                + "\"sessionToken\":\"" + token + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roundId").isNotEmpty())
                .andExpect(jsonPath("$.state").value("WAITING"))
                .andExpect(jsonPath("$.seatsTaken").value(1))
                .andExpect(jsonPath("$.seatsTotal").value(6))
                .andReturn();
        String roundId = new tools.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("roundId").asText();
        createdRounds.add(roundId);
        return roundId;
    }

    private void join(String roundId, String token) throws Exception {
        mockMvc.perform(post("/api/rooms/join")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\",\"sessionToken\":\"" + token + "\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void lobbyListsSoloAndWaitingRooms() throws Exception {
        String ana = playerToken("ana");
        mockMvc.perform(post("/api/rounds")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startingCapital\":100000,\"durationMinutes\":30,\"preset\":\"REALISTIC\"}"))
                .andExpect(status().isCreated());
        String roomId = createRoom(ana);

        MvcResult listing = mockMvc.perform(post("/api/rounds/live")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        String body = listing.getResponse().getContentAsString();
        assertTrue(body.contains(roomId), "the waiting room is listed");
        mockMvc.perform(post("/api/rounds/live")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.roundId == '" + roomId + "')].kind").value("room"))
                .andExpect(jsonPath("$[?(@.roundId == '" + roomId + "')].state").value("WAITING"))
                .andExpect(jsonPath("$[?(@.roundId == '" + roomId + "')].seatsTaken").value(1))
                .andExpect(jsonPath("$[?(@.roundId == '" + roomId + "')].seatsTotal").value(6))
                .andExpect(jsonPath("$[?(@.roundId == '" + roomId + "')].preset").value("REALISTIC"));
    }

    @Test
    void joinMasterStartAndBothRefusals() throws Exception {
        String ana = playerToken("ana");
        String bo = playerToken("bo");
        String carlo = playerToken("carlo");
        String roomId = createRoom(ana);

        // Second player joins the waiting room.
        mockMvc.perform(post("/api/rooms/join")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roomId + "\",\"sessionToken\":\"" + bo + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seat").value("human-2"))
                .andExpect(jsonPath("$.state").value("WAITING"));

        // Stranger start is refused and the clock stays held.
        mockMvc.perform(post("/api/rooms/start")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roomId + "\",\"sessionToken\":\"" + carlo + "\"}"))
                .andExpect(status().isForbidden());
        assertTrue(game.runningRound(roomId).orElseThrow().state()
                == com.virtualinvestor.core.domain.RoundState.WAITING);

        // Master starts the partial room.
        mockMvc.perform(post("/api/rooms/start")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roomId + "\",\"sessionToken\":\"" + ana + "\"}"))
                .andExpect(status().isNoContent());

        // Live room refuses the late join as started.
        mockMvc.perform(post("/api/rooms/join")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roomId + "\",\"sessionToken\":\"" + carlo + "\"}"))
                .andExpect(status().isConflict());

        // Unknown room is 404.
        mockMvc.perform(post("/api/rooms/join")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"round-nope\",\"sessionToken\":\"" + carlo + "\"}"))
                .andExpect(status().isNotFound());

        // Missing token is 401.
        mockMvc.perform(post("/api/rooms")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startingCapital\":100000,\"durationMinutes\":30,\"preset\":\"REALISTIC\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void fullRoomStartsItself() throws Exception {
        String master = playerToken("master");
        String roomId = createRoom(master);
        for (int i = 0; i < 5; i++) {
            join(roomId, playerToken("guest" + i));
        }
        // The claiming sixth join autostarted the room.
        assertTrue(game.runningRound(roomId).orElseThrow().state()
                == com.virtualinvestor.core.domain.RoundState.RUNNING);
        // A seventh claimant finds no free seats: the room already runs.
        mockMvc.perform(post("/api/rooms/join")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roomId + "\",\"sessionToken\":\""
                                + playerToken("late") + "\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void expiredRoomVanishes() throws Exception {
        String ana = playerToken("ana");
        String roomId = createRoom(ana);

        game.expireRooms(System.currentTimeMillis() + GameService.ROOM_TTL_MILLIS + 1L);

        assertTrue(game.runningRound(roomId).isEmpty(), "the held world is forgotten");
        MvcResult listing = mockMvc.perform(post("/api/rounds/live")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andReturn();
        assertTrue(!listing.getResponse().getContentAsString().contains(roomId),
                "the expired room leaves the lobby");
    }

    @Test
    void startedRoomSurvivesExpiry() throws Exception {
        String ana = playerToken("ana");
        String roomId = createRoom(ana);
        mockMvc.perform(post("/api/rooms/start")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roomId + "\",\"sessionToken\":\"" + ana + "\"}"))
                .andExpect(status().isNoContent());

        game.expireRooms(System.currentTimeMillis() + GameService.ROOM_TTL_MILLIS + 1L);

        assertTrue(game.runningRound(roomId).isPresent(), "a live room is not expired");
    }
}
