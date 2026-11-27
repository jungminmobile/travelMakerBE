package com.travelmaker.backend.generation.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * 외부 AI 일정 생성 서버 연동 설정 프로퍼티 ({@code app.ai.*}).
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.ai")
public class AiServerProperties {

    private String generator = "ai";
    private String baseUrl = "http://localhost:8000";
    private Duration connectTimeout = Duration.ofSeconds(5);
    private Duration readTimeout = Duration.ofSeconds(90);
}
