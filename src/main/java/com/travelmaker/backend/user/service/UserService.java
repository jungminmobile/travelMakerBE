package com.travelmaker.backend.user.service;

import com.travelmaker.backend.user.domain.User;
import com.travelmaker.backend.user.dto.UpdateProfileRequest;
import com.travelmaker.backend.user.dto.UserResponse;
import com.travelmaker.backend.user.dto.UserSearchResponse;
import com.travelmaker.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {
    private final UserRepository userRepository;

    public UserResponse getMyProfile(UUID userId) {
        return userRepository.findById(userId).map(UserResponse::from)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."));
    }

    @Transactional
    public UserResponse updateMyProfile(UUID userId, UpdateProfileRequest request){
        if(request.nickname() == null){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "닉네임을 입력해주세요.");
        }

        String nickname = request.nickname().trim();
        if(nickname.isBlank() || nickname.length()>50){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "닉네임은 1~50자여야합니다.");
        }

        String profileImageUrl = request.profileImageUrl();
        if(profileImageUrl != null &&profileImageUrl.isBlank()){
            profileImageUrl = null;
        }

        User user = userRepository.findById(userId).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다.")
        );

        user.updateProfile(nickname,profileImageUrl);

        return UserResponse.from(user);
    }

    public List<UserSearchResponse> searchUsers(UUID userId, String email){
        if(email == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이메일을 입력하세요");
        }
        email = email.trim();
        if(email.isBlank()){
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "이메일을 입력하세요.");

        }

        return userRepository.findByEmailIgnoreCase(email)
            .filter(user -> !user.getId().equals(userId))
            .map(UserSearchResponse::from)
            .stream()
            .toList();
    }
}