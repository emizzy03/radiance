package com.example.service;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Privilege grant/revoke must change what HTTP Basic can actually do.
 *
 * <p>The HTTP role endpoints currently 500 on the success path
 * ({@code LazyInitializationException} because {@code assignRoleToUser} /
 * {@code removeRoleFromUser} are not {@code @Transactional} and open-in-view is
 * false). This test drives the service methods inside a transaction — the
 * intended production contract — then asserts the shopper's Basic credentials
 * gain and lose {@code ROLE_ADMIN} on {@code /api/auth/me}, the daily report,
 * and catalog writes. Do not replace this with an HTTP success-path assertion
 * until that production fix lands.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RoleGrantRevokeBasicAuthHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserService userService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private ObjectMapper objectMapper;

    private long register(String username, String email, String password) throws Exception {
        String body = String.format(
                "{\"username\":\"%s\",\"email\":\"%s\",\"password\":\"%s\"}",
                username, email, password);
        MvcResult result = mockMvc.perform(post("/api/users")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.get("id").asLong();
    }

    @Test
    void assignRoleAdmin_grantsAdminHttpAccess_andRemovingItRevokes() throws Exception {
        String username = "cov23roleshop";
        String password = "password123";
        long id = register(username, "cov23roleshop@x.com", password);

        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic(username, password)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/products").with(httpBasic(username, password))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cov23Denied\",\"description\":\"d\",\"price\":1.00,"
                                + "\"quantity\":1,\"image\":\"/media/x.png\"}"))
                .andExpect(status().isForbidden());

        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.executeWithoutResult(status -> userService.assignRoleToUser(id, "ROLE_ADMIN"));

        mockMvc.perform(get("/api/auth/me").with(httpBasic(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.roles", hasItem("ROLE_ADMIN")));
        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic(username, password)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/products").with(httpBasic(username, password))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cov23GrantedLamp\",\"description\":\"d\",\"price\":4.50,"
                                + "\"quantity\":2,\"image\":\"/media/x.png\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cov23GrantedLamp"));

        tx.executeWithoutResult(status -> userService.removeRoleFromUser(id, "ROLE_ADMIN"));

        mockMvc.perform(get("/api/auth/me").with(httpBasic(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles", not(hasItem("ROLE_ADMIN"))));
        mockMvc.perform(get("/api/purchases/daily-report").with(httpBasic(username, password)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/products").with(httpBasic(username, password))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Cov23Revoked\",\"description\":\"d\",\"price\":1.00,"
                                + "\"quantity\":1,\"image\":\"/media/x.png\"}"))
                .andExpect(status().isForbidden());
    }
}
