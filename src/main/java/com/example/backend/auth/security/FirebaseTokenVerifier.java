package com.example.backend.auth.security;

import com.example.backend.auth.dto.FirebaseIdentity;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class FirebaseTokenVerifier {
    private final ObjectProvider<FirebaseAuth> firebaseAuth;

    public FirebaseTokenVerifier(ObjectProvider<FirebaseAuth> firebaseAuth) {
        this.firebaseAuth = firebaseAuth;
    }

    public FirebaseIdentity verify(String idToken) {
        try {
            FirebaseToken token = firebaseAuth.getObject().verifyIdToken(idToken, true);
            Object firebaseClaim = token.getClaims().get("firebase");
            if (!(firebaseClaim instanceof Map<?, ?> firebase)
                    || !"google.com".equals(firebase.get("sign_in_provider"))) {
                throw new BadCredentialsException("Google 로그인이 필요합니다.");
            }
            if (!token.isEmailVerified() || token.getEmail() == null) {
                throw new BadCredentialsException("확인된 이메일이 필요합니다.");
            }
            return new FirebaseIdentity(token.getUid(), token.getEmail(),
                    token.getName(), token.getPicture());
        } catch (FirebaseAuthException exception) {
            String code = String.valueOf(exception.getAuthErrorCode());
            if (code.equals("INVALID_ID_TOKEN") || code.equals("EXPIRED_ID_TOKEN")
                    || code.equals("REVOKED_ID_TOKEN") || code.equals("USER_DISABLED")
                    || code.equals("USER_NOT_FOUND")) {
                throw new BadCredentialsException("유효하지 않은 인증 토큰입니다.", exception);
            }
            throw new AuthenticationServiceException("인증 서비스에 연결할 수 없습니다.", exception);
        } catch (BeansException exception) {
            throw new AuthenticationServiceException("서버의 Firebase 설정을 확인해 주세요.", exception);
        } catch (IllegalArgumentException exception) {
            throw new BadCredentialsException("잘못된 인증 토큰입니다.", exception);
        }
    }
}