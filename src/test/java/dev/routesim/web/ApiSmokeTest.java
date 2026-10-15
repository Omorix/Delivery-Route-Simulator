package dev.routesim.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiSmokeTest {
    @Autowired
    MockMvc mvc;

    @Test
    void stateComesWithAPlanAndRoutes() throws Exception {
        mvc.perform(get("/api/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan.routes").isNotEmpty())
                .andExpect(jsonPath("$.snapshot.stats.totalOrders").isNumber());
    }

    @Test
    void compareShowsOptimiserBeatingGreedy() throws Exception {
        mvc.perform(get("/api/compare"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.savingsPercent").isNumber())
                .andExpect(jsonPath("$.optimised.distanceKm").isNumber());
    }

    @Test
    void unknownAlgorithmIsA400() throws Exception {
        mvc.perform(post("/api/algorithm").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"nope\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void oversizedParcelIsA400() throws Exception {
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"lat\":51.5,\"lng\":-0.1,\"size\":9999}"))
                .andExpect(status().isBadRequest());
    }
}
