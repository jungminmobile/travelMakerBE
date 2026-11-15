package com.example.backend.auth;

import com.example.backend.auth.security.FirebaseTokenVerifier;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.BadCredentialsException;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FirebaseTokenVerifierTest {
    @Mock private ObjectProvider<FirebaseAuth> provider;
    @Mock private FirebaseAuth firebaseAuth;
    @Mock private FirebaseToken token;
    private FirebaseTokenVerifier verifier;

    @BeforeEach
    void setUp() throws Exception {
        verifier = new FirebaseTokenVerifier(provider);
        when(provider.getObject()).thenReturn(firebaseAuth);
        when(firebaseAuth.verifyIdToken("token", true)).thenReturn(token);
    }

    @Test
    void acceptsVerifiedGoogleIdentity() {
        when(token.getClaims()).thenReturn(Map.of("firebase", Map.of("sign_in_provider", "google.com")));
        when(token.isEmailVerified()).thenReturn(true);
        when(token.getEmail()).thenReturn("a@example.com");
        when(token.getUid()).thenReturn("uid-a");
        assertThat(verifier.verify("token").uid()).isEqualTo("uid-a");
    }

    @Test
    void rejectsOtherProvider() {
        when(token.getClaims()).thenReturn(Map.of("firebase", Map.of("sign_in_provider", "password")));
        assertThatThrownBy(() -> verifier.verify("token")).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsUnverifiedEmail() {
        when(token.getClaims()).thenReturn(Map.of("firebase", Map.of("sign_in_provider", "google.com")));
        when(token.isEmailVerified()).thenReturn(false);
        assertThatThrownBy(() -> verifier.verify("token")).isInstanceOf(BadCredentialsException.class);
    }
}