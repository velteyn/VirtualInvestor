package com.virtualinvestor.edge.api;

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
}
