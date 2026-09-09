package com.example.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.example.entity.ErrorResponse;

/**
 * Locks the security contract of {@link GlobalExceptionHandler}: unexpected
 * failures return a generic 500 that does not leak internals; oversized uploads
 * are 413; authz/authn exceptions are re-thrown so Spring Security can emit
 * 403/401 instead of being masked as 500.
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private MockMvc mockMvc;

    @RestController
    static class BoomController {
        @GetMapping("/boom")
        public String boom() {
            throw new RuntimeException("SQLException: leaked password hash abc123");
        }
    }

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new BoomController())
                .setControllerAdvice(handler)
                .build();
    }

    @Test
    void unexpectedException_returnsGeneric500WithoutLeakingCause() throws Exception {
        MvcResult result = mockMvc.perform(get("/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred"))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).doesNotContain("SQLException");
        assertThat(body).doesNotContain("abc123");
        assertThat(body).doesNotContain("RuntimeException");
        assertThat(body).doesNotContain("password hash");
    }

    @Test
    void maxUploadSize_returnsPayloadTooLarge() {
        ResponseEntity<ErrorResponse> response =
                handler.handleMaxUploadSize(new MaxUploadSizeExceededException(1));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).isEqualTo("Uploaded file is too large");
        assertThat(response.getBody().getErrors()).isNull();
    }

    @Test
    void accessDenied_isRethrownForSecurityFilterChain() {
        AccessDeniedException ex = new AccessDeniedException("denied");
        assertThatThrownBy(() -> handler.handleAccessDenied(ex)).isSameAs(ex);
    }

    @Test
    void authenticationFailure_isRethrownForSecurityFilterChain() {
        BadCredentialsException ex = new BadCredentialsException("bad");
        assertThatThrownBy(() -> handler.handleAuthentication(ex))
                .isInstanceOf(AuthenticationException.class)
                .isSameAs(ex);
    }
}
