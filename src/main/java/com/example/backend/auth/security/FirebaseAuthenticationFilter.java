package com.example.backend.auth.security;

import com.example.backend.auth.dto.FirebaseIdentity;
import com.example.backend.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.List;

public class FirebaseAuthenticationFilter extends OncePerRequestFilter {
    private final FirebaseTokenVerifier verifier;
    private final SecurityErrorWriter errorWriter;
    private final UserRepository users;

    public FirebaseAuthenticationFilter(FirebaseTokenVerifier verifier, SecurityErrorWriter errorWriter, UserRepository users) {
        this.verifier = verifier;
        this.errorWriter = errorWriter;
        this.users = users;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
                && (request.getContextPath() + "/api/v1/auth/google").equals(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null) {
            filterChain.doFilter(request, response);
            return;
        }
        if (!header.regionMatches(true, 0, "Bearer ", 0, 7)
                || header.substring(7).isBlank()) {
            errorWriter.write(response, 401, "INVALID_TOKEN", "Bearer 토큰이 필요합니다.");
            return;
        }

        FirebaseIdentity identity;
        try {
            identity = verifier.verify(header.substring(7).trim());
        } catch (AuthenticationServiceException exception) {
            errorWriter.write(response, 503, "AUTH_UNAVAILABLE", "인증 서비스를 사용할 수 없습니다.");
            return;
        } catch (AuthenticationException exception) {
            errorWriter.write(response, 401, "INVALID_TOKEN", "로그인 정보를 확인해 주세요.");
            return;
        }

        var member = users.findByFirebaseUid(identity.uid());
        if (member.isEmpty()) {
            errorWriter.write(response, 401, "MEMBER_NOT_REGISTERED", "구글 로그인 API를 먼저 호출해 주세요.");
            return;
        }
        var principal = new AuthenticatedUser(member.get().getId());
        var authentication = new UsernamePasswordAuthenticationToken(principal, null, List.of());
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        try {
            filterChain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }
}