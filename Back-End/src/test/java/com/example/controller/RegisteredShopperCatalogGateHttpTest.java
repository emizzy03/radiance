package com.example.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Registration creates an account with no roles. Catalog writes are
 * {@code hasRole("ADMIN")} in the filter chain — a mock {@code roles("USER")}
 * caller is not the same as a real Basic-authenticated shopper with an empty
 * authority set. That shopper must still be able to check out (permitAll) and
 * must get 401 from {@code /api/auth/me} with the wrong password (admin login).
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegisteredShopperCatalogGateHttpTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String USERNAME = "cov22shopper";
    private static final String PASSWORD = "password123";

    private void registerShopper() throws Exception {
        mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + USERNAME
                                + "\",\"email\":\"cov22shopper@x.com\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isCreated());
    }

    private long createProduct() throws Exception {
        String json = "{\"name\":\"Cov22ShopperLamp\",\"description\":\"d\",\"price\":9.50,"
                + "\"quantity\":4,\"image\":\"/media/s.png\"}";
        MvcResult created = mockMvc.perform(post("/api/products").with(user("boss").roles("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();
    }

    @Test
    void registeredShopper_cannotMutateCatalogButCanCheckout() throws Exception {
        registerShopper();
        long id = createProduct();

        mockMvc.perform(get("/api/auth/me").with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(USERNAME))
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));

        String createJson =
                "{\"name\":\"StolenSku\",\"description\":\"d\",\"price\":1.00,\"quantity\":1,\"image\":\"x.png\"}";
        mockMvc.perform(post("/api/products").with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content(createJson))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/api/products/" + id).with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"price\":0.01}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/products/" + id).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isForbidden());

        MockMultipartFile png = new MockMultipartFile(
                "file", "photo.png", "image/png",
                new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a, 1, 2});
        mockMvc.perform(multipart("/api/products/images").file(png).with(httpBasic(USERNAME, PASSWORD)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/purchases").with(httpBasic(USERNAME, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + id + ",\"quantity\":1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.product.id").value((int) id));

        mockMvc.perform(get("/api/products/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov22ShopperLamp"))
                .andExpect(jsonPath("$.price").value(9.50))
                .andExpect(jsonPath("$.quantity").value(3));
    }

    @Test
    void authMe_wrongPasswordIsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me").with(httpBasic("admin", "not-the-admin-password")))
                .andExpect(status().isUnauthorized());
    }
}
