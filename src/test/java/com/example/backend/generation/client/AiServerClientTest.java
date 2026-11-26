package com.example.backend.generation.client;

import com.example.backend.generation.client.dto.AiItineraryRequest;
import com.example.backend.generation.client.dto.AiItineraryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiServerClientTest {

    private MockRestServiceServer mockServer;
    private AiServerClient aiServerClient;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ai-server.test");
        mockServer = MockRestServiceServer.bindTo(builder).build();
        aiServerClient = new AiServerClient(builder.build(), JsonMapper.builder().build());
    }

    private AiItineraryRequest sampleRequest() {
        return new AiItineraryRequest(
                "일본",
                "도쿄",
                "2026-11-03",
                "2026-11-05",
                List.of(
                        new AiItineraryRequest.Participant(
                                "user-1",
                                "진수",
                                Map.of("q_food", List.of("ramen"), "q_spot", List.of("shrine"))
                        ),
                        new AiItineraryRequest.Participant(
                                "user-2",
                                "민지",
                                Map.of("q_food", List.of("cafe"), "q_spot", List.of("shopping"))
                        )
                ),
                new AiItineraryRequest.Conditions("친구", "대중교통", "50만원-100만원", "적당히"),
                new AiItineraryRequest.Options(1)
        );
    }

    @Test
    @DisplayName("POST /itineraries 요청 시 백엔드 quizSelections 키와 camelCase 필드를 전송하고 common/alternate/separate 응답을 파싱한다")
    void generateItineraryParsesAllModes() {
        String responseJson = """
                {
                  "destination": "일본",
                  "city": "도쿄",
                  "days": [
                    {
                      "day": 1,
                      "date": "2026-11-03",
                      "title": "도쿄 1일차",
                      "schedule": [
                        {
                          "time": "10:00",
                          "endTime": "11:30",
                          "title": "센소지 방문",
                          "mode": "common",
                          "place": {
                            "id": "sensoji",
                            "name": "센소지",
                            "lat": 35.7148,
                            "lng": 139.7967,
                            "concept": "유적지"
                          },
                          "description": "아사쿠사 대표 사찰",
                          "reason": "전원이 선호하는 유적지 코스"
                        },
                        {
                          "time": "12:00",
                          "endTime": "13:30",
                          "title": "아사쿠사 라멘",
                          "mode": "alternate",
                          "place": {
                            "id": "asakusa_ramen",
                            "name": "요로이야 라멘",
                            "lat": 35.7121,
                            "lng": 139.7955,
                            "concept": "로컬맛집"
                          },
                          "description": "인기 라멘집",
                          "reason": "진수 취향 반영",
                          "pickedBy": ["user-1"]
                        },
                        {
                          "time": "14:30",
                          "endTime": "16:30",
                          "title": "취향별 분리 시간",
                          "mode": "separate",
                          "separate": [
                            {
                              "participantIds": ["user-1"],
                              "title": "우에노 박물관",
                              "place": {
                                "id": "ueno_museum",
                                "name": "도쿄국립박물관",
                                "lat": 35.7188,
                                "lng": 139.7765,
                                "concept": "박물관"
                              },
                              "description": "전시 관람",
                              "reason": "문화 탐방 선호"
                            },
                            {
                              "participantIds": ["user-2"],
                              "title": "시부야 쇼핑",
                              "place": {
                                "id": "shibuya_109",
                                "name": "시부야 스트리트",
                                "lat": 35.6595,
                                "lng": 139.7004,
                                "concept": "쇼핑"
                              },
                              "description": "트렌디 숍 투어",
                              "reason": "쇼핑 선호"
                            }
                          ]
                        }
                      ]
                    }
                  ]
                }
                """;

        mockServer.expect(requestTo("http://ai-server.test/itineraries"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.city").value("도쿄"))
                .andExpect(jsonPath("$.startDate").value("2026-11-03"))
                .andExpect(jsonPath("$.participants[0].quizSelections.q_food[0]").value("ramen"))
                .andExpect(jsonPath("$.conditions.tempo").value("적당히"))
                .andExpect(jsonPath("$.options.maxSeparateBlocksPerDay").value(1))
                .andRespond(withSuccess(responseJson, MediaType.APPLICATION_JSON));

        AiItineraryResponse result = aiServerClient.generateItinerary(sampleRequest());

        assertThat(result.city()).isEqualTo("도쿄");
        assertThat(result.days()).hasSize(1);
        assertThat(result.days().get(0).schedule()).hasSize(3);
        assertThat(result.days().get(0).schedule().get(1).pickedBy()).containsExactly("user-1");
        assertThat(result.days().get(0).schedule().get(2).separate()).hasSize(2);
        mockServer.verify();
    }

    @Test
    @DisplayName("400/500/502/503 AI 오류 응답의 code와 retryable을 정확히 매핑한다")
    void translatesStructuredErrorResponses() {
        assertErrorMapping(HttpStatus.BAD_REQUEST, "UNSUPPORTED_CITY", false);
        assertErrorMapping(HttpStatus.BAD_GATEWAY, "GENERATION_FAILED", true);
        assertErrorMapping(HttpStatus.SERVICE_UNAVAILABLE, "LLM_UNAVAILABLE", true);
        assertErrorMapping(HttpStatus.INTERNAL_SERVER_ERROR, "LLM_ERROR", false);
    }

    private void assertErrorMapping(HttpStatus status, String code, boolean expectedRetryable) {
        mockServer.reset();
        String errorJson = """
                {
                  "code": "%s",
                  "message": "오류 메시지 (%s)",
                  "retryable": %s
                }
                """.formatted(code, code, expectedRetryable);

        mockServer.expect(requestTo("http://ai-server.test/itineraries"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(status).contentType(MediaType.APPLICATION_JSON).body(errorJson));

        assertThatThrownBy(() -> aiServerClient.generateItinerary(sampleRequest()))
                .isInstanceOf(AiServerException.class)
                .satisfies(ex -> {
                    AiServerException aiEx = (AiServerException) ex;
                    assertThat(aiEx.getHttpStatus()).isEqualTo(status.value());
                    assertThat(aiEx.getErrorCode()).isEqualTo(code);
                    assertThat(aiEx.isRetryable()).isEqualTo(expectedRetryable);
                    assertThat(aiEx.getMessage()).contains(code);
                });
        mockServer.verify();
    }

    @Test
    @DisplayName("422 FastAPI 검증 오류 응답은 INVALID_AI_REQUEST / retryable=false로 변환한다")
    void translates422ValidationError() {
        String fastApi422 = """
                {"detail":[{"loc":["body","startDate"],"msg":"field required","type":"value_error.missing"}]}
                """;
        mockServer.expect(requestTo("http://ai-server.test/itineraries"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.UNPROCESSABLE_CONTENT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(fastApi422));

        assertThatThrownBy(() -> aiServerClient.generateItinerary(sampleRequest()))
                .isInstanceOf(AiServerException.class)
                .satisfies(ex -> {
                    AiServerException aiEx = (AiServerException) ex;
                    assertThat(aiEx.getHttpStatus()).isEqualTo(422);
                    assertThat(aiEx.getErrorCode()).isEqualTo("INVALID_AI_REQUEST");
                    assertThat(aiEx.isRetryable()).isFalse();
                });
        mockServer.verify();
    }

    @Test
    @DisplayName("GET /health 헬스체크는 200이면 true, 503이면 false를 반환한다")
    void healthCheck() {
        mockServer.expect(requestTo("http://ai-server.test/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"status\":\"ok\"}", MediaType.APPLICATION_JSON));
        assertThat(aiServerClient.isHealthy()).isTrue();
        mockServer.verify();

        mockServer.reset();
        mockServer.expect(requestTo("http://ai-server.test/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        assertThat(aiServerClient.isHealthy()).isFalse();
        mockServer.verify();
    }
}
