package com.example.backend.auth;

import com.example.backend.auth.dto.FirebaseIdentity;
import com.example.backend.auth.security.FirebaseTokenVerifier;
import com.example.backend.user.domain.User;
import com.example.backend.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
class AuthApiTest {
    @Autowired private WebApplicationContext context;
    @Autowired private UserRepository users;
    @MockitoBean private FirebaseTokenVerifier verifier;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        users.deleteAll();
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        when(verifier.verify("valid-token")).thenReturn(
                new FirebaseIdentity("uid-a", "a@example.com", "지원", null));
    }

    private MockHttpServletRequestBuilder login(String token) {
        return post("/api/v1/auth/google").contentType("application/json")
                .content("{\"id_token\":\"" + token + "\"}");
    }

    @Test
    void missingHeaderCannotAccessMe() throws Exception {
        mvc.perform(get("/api/v1/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void missingBodyTokenIs400() throws Exception {
        mvc.perform(post("/api/v1/auth/google").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(login(" ")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/auth/google").contentType("application/json").content("{"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidTokensAre401() throws Exception {
        when(verifier.verify("bad")).thenThrow(new BadCredentialsException("invalid"));
        mvc.perform(login("bad")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer bad"))
                .andExpect(status().isUnauthorized());
        assertThat(users.count()).isZero();
    }

    @Test
    void unavailableFirebaseIs503() throws Exception {
        when(verifier.verify("outage")).thenThrow(new AuthenticationServiceException("unavailable"));
        mvc.perform(login("outage")).andExpect(status().isServiceUnavailable());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer outage"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void firstLoginCreatesAndRepeatLoginReusesMember() throws Exception {
        mvc.perform(login("valid-token"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.is_new_user").value(true))
                .andExpect(jsonPath("$.data.user.email").value("a@example.com"))
                .andExpect(jsonPath("$.data.user.created_at").exists());
        mvc.perform(login("valid-token")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.is_new_user").value(false));
        assertThat(users.count()).isEqualTo(1);
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(users.findByFirebaseUid("uid-a").orElseThrow().getId().toString()))
                .andExpect(jsonPath("$.data.created_at").exists())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"profile_image_url\"")))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void verifiedButUnregisteredUserIs401() throws Exception {
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer valid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("MEMBER_NOT_REGISTERED"));
    }

    @Test
    void existingEmailIsNotAutomaticallyLinked() throws Exception {
        users.saveAndFlush(User.builder().email("a@example.com").nickname("기존 회원").build());
        mvc.perform(login("valid-token")).andExpect(status().isConflict());
        assertThat(users.findByFirebaseUid("uid-a")).isEmpty();
    }

    @Test
    void meDoesNotUseUntrustedUserIdParameter() throws Exception {
        var other = users.saveAndFlush(User.builder().firebaseUid("uid-other")
                .email("other@example.com").nickname("다른 사람").build());
        mvc.perform(login("valid-token")).andExpect(status().isCreated());
        mvc.perform(get("/api/v1/users/me").param("userId", other.getId().toString())
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("a@example.com"));
    }

    @Test
    void corsPreflightAllowsFrontend() throws Exception {
        mvc.perform(options("/api/v1/auth/google")
                        .header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
    }
}