package com.example.backend.auth.security;

import com.example.backend.common.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;

@Component
public class SecurityErrorWriter {
    private final JsonMapper jsonMapper;

    public SecurityErrorWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletResponse response, int status, String code, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(jsonMapper.writeValueAsString(ApiResponse.fail(code, message)));
    }
}