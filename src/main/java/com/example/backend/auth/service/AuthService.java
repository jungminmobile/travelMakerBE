package com.example.backend.auth.service;

import com.example.backend.auth.dto.LoginResponse;
import com.example.backend.auth.security.FirebaseTokenVerifier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final FirebaseTokenVerifier verifier;
    private final MemberRegistrationService registrationService;

    public LoginResponse login(String idToken) {
        var identity = verifier.verify(idToken);
        return registrationService.findOrRegister(identity);
    }
}