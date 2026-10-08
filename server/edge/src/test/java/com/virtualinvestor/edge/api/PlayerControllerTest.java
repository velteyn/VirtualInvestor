package com.virtualinvestor.edge.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.virtualinvestor.edge.EdgeApplication;
import com.virtualinvestor.edge.game.PlayerService;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = EdgeApplication.class)
@AutoConfigureMockMvc
class PlayerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PlayerService players;

    private static String username() {
        return "api-" + UUID.randomUUID().toString().substring(0, 8);
    }

    private MvcResult register(String username, String bankId) throws Exception {
        return mockMvc.perform(post("/api/players/register")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"secret-1\",\"bankId\":\""
                                + bankId + "\"}"))
                .andReturn();
    }

    @Test
    void registerPendsApproval() throws Exception {
        String name = username();

        mockMvc.perform(post("/api/players/register")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"password\":\"secret-1\",\"bankId\":\"meridian\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").value(name))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.bankId").value("meridian"));
    }

    @Test
    void duplicateAndUnknownBankAreRejected() throws Exception {
        String name = username();
        register(name, "first-ledger");

        mockMvc.perform(post("/api/players/register")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"password\":\"secret-2\",\"bankId\":\"meridian\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/players/register")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username()
                                + "\",\"password\":\"secret-1\",\"bankId\":\"no-such-bank\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void pendingLoginRefusedApprovedLoginIssuesToken() throws Exception {
        String name = username();
        register(name, "apex");

        mockMvc.perform(post("/api/players/login")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"password\":\"secret-1\"}"))
                .andExpect(status().isForbidden());

        players.store().approve(players.store().findByUsername(name).orElseThrow().playerId());

        mockMvc.perform(post("/api/players/login")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"password\":\"secret-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.sessionToken").isNotEmpty());
    }

    @Test
    void forcedPasswordChangeFlow() throws Exception {
        String name = username();
        register(name, "meridian");
        players.store().approve(players.store().findByUsername(name).orElseThrow().playerId());
        players.store().resetPassword(players.store().findByUsername(name).orElseThrow().playerId(), "temp-1");

        mockMvc.perform(post("/api/players/login")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"password\":\"temp-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(true));

        mockMvc.perform(post("/api/players/change-password")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"oldPassword\":\"wrong\",\"newPassword\":\"mine-1\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/players/change-password")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"oldPassword\":\"temp-1\",\"newPassword\":\"mine-1\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/players/login")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"password\":\"mine-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mustChangePassword").value(false));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        String name = username();
        register(name, "meridian");
        players.store().approve(players.store().findByUsername(name).orElseThrow().playerId());

        mockMvc.perform(post("/api/players/login")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/players/login")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"nobody-" + UUID.randomUUID() + "\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void banksAreListed() throws Exception {
        mockMvc.perform(post("/api/players/banks")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].bankId").value("apex"))
                .andExpect(jsonPath("$[1].bankId").value("first-ledger"))
                .andExpect(jsonPath("$[2].bankId").value("meridian"));
    }
}
