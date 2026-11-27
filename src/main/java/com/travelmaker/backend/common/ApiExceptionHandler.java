package com.travelmaker.backend.common;

import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest().body(ApiResponse.fail(
                "INVALID_REQUEST", "id_token을 포함한 올바른 JSON 요청이 필요합니다."));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException exception) {
        if (exception instanceof AuthenticationServiceException) {
            return ResponseEntity.status(503).body(ApiResponse.fail(
                    "AUTH_UNAVAILABLE", "인증 서비스를 사용할 수 없습니다."));
        }
        return ResponseEntity.status(401).body(ApiResponse.fail(
                "INVALID_TOKEN", "로그인 정보를 확인해 주세요."));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiResponse<Void>> handleStatus(ResponseStatusException exception) {
        return ResponseEntity.status(exception.getStatusCode()).body(
                ApiResponse.fail("HTTP_" + exception.getStatusCode().value(),
                        exception.getReason()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConflict(DataIntegrityViolationException exception) {
        return ResponseEntity.status(409).body(ApiResponse.fail(
                "DATA_CONFLICT", "이미 사용 중인 정보이거나 동시에 처리된 요청입니다. 다시 확인해 주세요."));
    }
}