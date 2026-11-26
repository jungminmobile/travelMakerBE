package com.example.backend.generation.client;

import com.example.backend.generation.client.dto.AiErrorResponse;
import com.example.backend.generation.client.dto.AiItineraryRequest;
import com.example.backend.generation.client.dto.AiItineraryResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.json.JsonMapper;

/**
 * AI 서버({@code POST /itineraries}, {@code GET /health}) HTTP 클라이언트.
 */
@Slf4j
@Component
public class AiServerClient {

    private final RestClient restClient;
    private final JsonMapper jsonMapper;

    public AiServerClient(@Qualifier("aiRestClient") RestClient restClient, JsonMapper jsonMapper) {
        this.restClient = restClient;
        this.jsonMapper = jsonMapper;
    }

    /**
     * AI 서버에 일정 생성을 동기 요청한다.
     */
    public AiItineraryResponse generateItinerary(AiItineraryRequest request) {
        try {
            AiItineraryResponse response = restClient.post()
                    .uri("/itineraries")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiItineraryResponse.class);

            if (response == null || response.days() == null || response.days().isEmpty()) {
                throw new AiServerException(
                        502,
                        "INVALID_AI_RESPONSE",
                        true,
                        "AI 서버로부터 유효한 일정 응답을 받지 못했습니다."
                );
            }
            return response;
        } catch (RestClientResponseException ex) {
            throw translateHttpError(ex);
        } catch (ResourceAccessException ex) {
            throw new AiServerException(
                    0,
                    "AI_SERVER_UNREACHABLE",
                    true,
                    "AI 서버와 통신할 수 없거나 응답 시간이 초과되었습니다.",
                    ex
            );
        }
    }

    /**
     * AI 서버 헬스체크({@code GET /health})를 호출한다.
     */
    public boolean isHealthy() {
        try {
            restClient.get()
                    .uri("/health")
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception ex) {
            log.debug("AI 서버 헬스체크 실패", ex);
            return false;
        }
    }

    private AiServerException translateHttpError(RestClientResponseException ex) {
        int status = ex.getStatusCode().value();
        if (status == 422) {
            return new AiServerException(
                    422,
                    "INVALID_AI_REQUEST",
                    false,
                    "AI 서버 요청 형식이 올바르지 않습니다.",
                    ex
            );
        }

        String rawBody = ex.getResponseBodyAsString();
        if (rawBody != null && !rawBody.isBlank()) {
            try {
                AiErrorResponse parsed = jsonMapper.readValue(rawBody, AiErrorResponse.class);
                if (parsed != null && parsed.code() != null && !parsed.code().isBlank()) {
                    boolean retryable = parsed.retryable() != null
                            ? parsed.retryable()
                            : isDefaultRetryable(status);
                    String message = (parsed.message() != null && !parsed.message().isBlank())
                            ? parsed.message()
                            : "AI 서버 요청에 실패했습니다. (HTTP " + status + ")";
                    return new AiServerException(status, parsed.code(), retryable, message, ex);
                }
            } catch (Exception parseEx) {
                log.debug("AI 오류 응답 JSON 파싱 실패 (status={}): {}", status, rawBody, parseEx);
            }
        }

        return new AiServerException(
                status,
                "AI_HTTP_" + status,
                isDefaultRetryable(status),
                "AI 서버 요청에 실패했습니다. (HTTP " + status + ")",
                ex
        );
    }

    private boolean isDefaultRetryable(int status) {
        return status == 502 || status == 503 || status == 504;
    }
}
