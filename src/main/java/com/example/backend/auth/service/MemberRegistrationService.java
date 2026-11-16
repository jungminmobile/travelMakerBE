package com.example.backend.auth.service;

import com.example.backend.auth.dto.FirebaseIdentity;
import com.example.backend.auth.dto.LoginResponse;
import com.example.backend.user.domain.User;
import com.example.backend.user.dto.UserResponse;
import com.example.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberRegistrationService {
    private final UserRepository userRepository;

    @Transactional
    public LoginResponse findOrRegister(FirebaseIdentity identity) {
        var existing = userRepository.findByFirebaseUid(identity.uid());
        if (existing.isPresent()) {
            return new LoginResponse(false, UserResponse.from(existing.get()));
        }
        if (userRepository.existsByEmail(identity.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "같은 이메일의 기존 회원이 있습니다. 계정 연결을 확인해 주세요.");
        }
        if (identity.email().length() > 255) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이메일이 너무 깁니다.");
        }
        String nickname = identity.name();
        if (nickname == null || nickname.isBlank()) {
            nickname = "여행자";
        }
        nickname = nickname.strip();
        int end = nickname.offsetByCodePoints(0, Math.min(50, nickname.codePointCount(0, nickname.length())));
        nickname = nickname.substring(0, end);

        User user = User.builder()
                .firebaseUid(identity.uid())
                .email(identity.email())
                .nickname(nickname)
                .profileImageUrl(identity.picture())
                .build();
        User saved = userRepository.saveAndFlush(user);
        return new LoginResponse(true, UserResponse.from(saved));
    }

}