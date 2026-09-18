package com.example.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * {@code Product.id} is writable JSON (unlike {@code User.id}). PATCH must
 * still key off the path id: a body {@code id} must not retarget or overwrite
 * a different catalog row. Complements the user PATCH id-retarget test.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ProductPatchIdMassAssignmentHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private long createProduct(String name, String image) throws Exception {
        String json = "{\"name\":\"" + name + "\",\"description\":\"keep-" + name + "\","
                + "\"price\":12.00,\"quantity\":7,\"image\":\"" + image + "\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void patchProduct_bodyIdDoesNotRetargetAnotherSku() throws Exception {
        long pathId = createProduct("Cov22PatchTargetLamp", "/media/target.png");
        long otherId = createProduct("Cov22PatchVictimMug", "/media/victim.png");

        String sneaky = "{\"id\":" + otherId + ",\"name\":\"Cov22PatchedLamp\"}";
        mockMvc.perform(patch("/api/products/" + pathId).with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(sneaky))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) pathId))
                .andExpect(jsonPath("$.name").value("Cov22PatchedLamp"))
                .andExpect(jsonPath("$.image").value("/media/target.png"))
                .andExpect(jsonPath("$.quantity").value(7));

        mockMvc.perform(get("/api/products/" + pathId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) pathId))
                .andExpect(jsonPath("$.name").value("Cov22PatchedLamp"))
                .andExpect(jsonPath("$.description").value("keep-Cov22PatchTargetLamp"));

        mockMvc.perform(get("/api/products/" + otherId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) otherId))
                .andExpect(jsonPath("$.name").value("Cov22PatchVictimMug"))
                .andExpect(jsonPath("$.image").value("/media/victim.png"))
                .andExpect(jsonPath("$.quantity").value(7));
    }
}
