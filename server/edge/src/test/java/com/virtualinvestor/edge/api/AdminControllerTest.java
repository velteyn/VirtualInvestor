package com.virtualinvestor.edge.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.edge.EdgeApplication;
import com.virtualinvestor.edge.game.PlayerService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = EdgeApplication.class, properties = "VI_ADMIN_PASSWORD=test-master")
@AutoConfigureMockMvc
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlayerService players;

    @Autowired
    private com.virtualinvestor.edge.game.GameService game;

    private String adminToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/login")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"test-master\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.adminToken").isNotEmpty())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.substring(body.indexOf(":\"") + 2, body.lastIndexOf("\""));
    }

    @Test
    void wrongMasterPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/admin/login")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void backyardWithoutAdminTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/admin/login")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        // Any other /api/admin/* call without a token is refused, even with a player token.
        mockMvc.perform(post("/api/admin/players")
                        .header("X-API-Version", "1")
                        .header("X-Admin-Token", "player-token-is-not-admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/admin/players")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminTokenPassesTheGate() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/admin/players")
                        .header("X-API-Version", "1")
                        .header("X-Admin-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void approveRejectAndResetsFlow() throws Exception {
        String token = adminToken();
        String name = "adm-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        String playerId = players.store().register(name, "secret-1", "meridian").playerId();

        // Pending player is listed and approved through the backyard.
        mockMvc.perform(post("/api/admin/players/approve")
                        .header("X-API-Version", "1")
                        .header("X-Admin-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + playerId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Login now issues a token; rejecting invalidates it.
        String loginToken = players.login(name, "secret-1").sessionToken();
        mockMvc.perform(post("/api/admin/players/reject")
                        .header("X-API-Version", "1")
                        .header("X-Admin-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + playerId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"));
        assertTrue(players.playerForToken(loginToken).isEmpty(), "reject invalidates sessions");

        // Password reset: old password dies, new one works.
        mockMvc.perform(post("/api/admin/players/reset-password")
                        .header("X-API-Version", "1")
                        .header("X-Admin-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + playerId + "\",\"newPassword\":\"secret-2\"}"))
                .andExpect(status().isOk());
        assertFalse(players.store().checkPassword(name, "secret-1"));
        assertTrue(players.store().checkPassword(name, "secret-2"));

        // Purse reset: funded purse returns to starting capital, holdings gone.
        players.store().savePurse(playerId, 10.0, 5.0,
                Map.of("NVDO", com.virtualinvestor.persist.player.PlayerStore.holding("NVDO", 3L, 600.0)));
        mockMvc.perform(post("/api/admin/players/reset-purse")
                        .header("X-API-Version", "1")
                        .header("X-Admin-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + playerId + "\",\"startingCapital\":100000}"))
                .andExpect(status().isOk());
        var purse = players.store().loadPurse(playerId);
        assertEquals(100_000.0, purse.cash());
        assertTrue(purse.holdings().isEmpty());

        // Unknown players are 404/400, not 500.
        mockMvc.perform(post("/api/admin/players/approve")
                        .header("X-API-Version", "1")
                        .header("X-Admin-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"ghost\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void createUpdateAndDeleteFlow() throws Exception {
        String token = adminToken();
        String name = "adm-new-" + java.util.UUID.randomUUID().toString().substring(0, 8);

        MvcResult created = mockMvc.perform(post("/api/admin/players/create")
                        .header("X-API-Version", "1")
                        .header("X-Admin-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"password\":\"temp-1\",\"bankId\":\"apex\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andReturn();
        String body = created.getResponse().getContentAsString();
        String playerId = body.split("\"playerId\":\"")[1].split("\"")[0];
        assertTrue(players.store().findByUsername(name).orElseThrow().mustChangePassword());

        mockMvc.perform(post("/api/admin/players/update")
                        .header("X-API-Version", "1")
                        .header("X-Admin-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + playerId + "\",\"username\":\"" + name
                                + "-renamed\",\"bankId\":\"meridian\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name + "-renamed"))
                .andExpect(jsonPath("$.bankId").value("meridian"));

        mockMvc.perform(post("/api/admin/players/delete")
                        .header("X-API-Version", "1")
                        .header("X-Admin-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"" + playerId + "\"}"))
                .andExpect(status().isNoContent());
        assertTrue(players.store().findByUsername(name + "-renamed").isEmpty());

        mockMvc.perform(post("/api/admin/players/delete")
                        .header("X-API-Version", "1")
                        .header("X-Admin-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"playerId\":\"ghost\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void pauseAndSettleEarlyActOnALiveRound() throws Exception {
        String token = adminToken();
        String roundId = game.createRound(new com.virtualinvestor.core.game.LaunchConfig(
                100_000.0, 30L, com.virtualinvestor.core.rules.RealismPreset.REALISTIC)).roundId();

        mockMvc.perform(post("/api/admin/rounds")
                        .header("X-API-Version", "1")
                        .header("X-Admin-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.roundId=='" + roundId + "')]").exists());

        mockMvc.perform(post("/api/admin/rounds/pause")
                        .header("X-API-Version", "1")
                        .header("X-Admin-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}"))
                .andExpect(status().isOk());
        assertEquals(com.virtualinvestor.core.domain.RoundState.PAUSED,
                game.runningRound(roundId).orElseThrow().state());

        mockMvc.perform(post("/api/admin/rounds/resume")
                        .header("X-API-Version", "1")
                        .header("X-Admin-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/admin/rounds/settle")
                        .header("X-API-Version", "1")
                        .header("X-Admin-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\"}"))
                .andExpect(status().isOk());
        assertTrue(game.runningRound(roundId).orElseThrow().settled());

        mockMvc.perform(post("/api/admin/rounds/settle")
                        .header("X-API-Version", "1")
                        .header("X-Admin-Token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"ghost\"}"))
                .andExpect(status().isNotFound());
    }
}
