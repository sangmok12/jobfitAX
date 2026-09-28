package com.jobfitax.backend.analysis;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Component
public class OpenAiJobFitClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiJobFitClient.class);
    private static final double LUNA_INPUT_PRICE_PER_MILLION = 0.10;
    private static final double LUNA_OUTPUT_PRICE_PER_MILLION = 0.50;

    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final String apiKey;
    private final String model;

    public OpenAiJobFitClient(
            ObjectMapper objectMapper,
            @Value("${openai.api-key:}") String apiKey,
            @Value("${openai.model:gpt-6-luna}") String model
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(15));
        requestFactory.setReadTimeout(Duration.ofMinutes(3));
        this.restClient = RestClient.builder()
                .baseUrl("https://api.openai.com/v1")
                .requestFactory(requestFactory)
                .build();
    }

    public AiResponse analyze(String systemPrompt, String userPrompt) {
        if (!StringUtils.hasText(apiKey)) {
            throw new AiAnalysisException("OpenAI API 키가 설정되지 않았습니다. 백엔드 실행 환경에 OPENAI_API_KEY를 설정해 주세요.");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("store", false);
        body.put("reasoning", Map.of("effort", "low"));
        body.put("max_output_tokens", 6000);
        body.put("input", List.of(
                Map.of("role", "system", "content", systemPrompt),
                Map.of("role", "user", "content", userPrompt)
        ));
        body.put("text", Map.of("format", Map.of(
                "type", "json_schema",
                "name", "job_fit_analysis",
                "strict", true,
                "schema", responseSchema()
        )));

        try {
            JsonNode response = restClient.post()
                    .uri("/responses")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);

            if (response == null) {
                throw new AiAnalysisException("OpenAI에서 빈 응답을 받았습니다.");
            }

            String outputText = findOutputText(response);
            JsonNode usage = response.path("usage");
            int inputTokens = usage.path("input_tokens").asInt();
            int outputTokens = usage.path("output_tokens").asInt();
            int totalTokens = usage.path("total_tokens").asInt(inputTokens + outputTokens);
            double estimatedCost = (inputTokens * LUNA_INPUT_PRICE_PER_MILLION
                    + outputTokens * LUNA_OUTPUT_PRICE_PER_MILLION) / 1_000_000d;

            log.info("OpenAI 분석 완료 model={}, inputTokens={}, outputTokens={}, totalTokens={}, estimatedCostUsd={}",
                    model, inputTokens, outputTokens, totalTokens, String.format("%.6f", estimatedCost));

            return new AiResponse(outputText, model, inputTokens, outputTokens, totalTokens, estimatedCost);
        } catch (RestClientResponseException exception) {
            log.warn("OpenAI API 요청 실패 status={}, response={}", exception.getStatusCode(), safeResponse(exception));
            throw new AiAnalysisException(apiErrorMessage(exception), exception);
        }
    }

    public <T> T parse(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JacksonException exception) {
            throw new AiAnalysisException("AI 분석 결과를 읽지 못했습니다. 잠시 후 다시 시도해 주세요.", exception);
        }
    }

    private String findOutputText(JsonNode response) {
        for (JsonNode output : response.path("output")) {
            for (JsonNode content : output.path("content")) {
                if ("output_text".equals(content.path("type").asText())) {
                    return content.path("text").asText();
                }
                if ("refusal".equals(content.path("type").asText())) {
                    throw new AiAnalysisException("AI가 이 분석 요청을 처리하지 못했습니다: " + content.path("refusal").asText());
                }
            }
        }
        throw new AiAnalysisException("OpenAI 응답에 분석 결과가 없습니다.");
    }

    private String apiErrorMessage(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        if (status == 401) return "OpenAI API 키가 올바르지 않습니다.";
        if (status == 429) return "OpenAI 사용 한도 또는 요청 한도에 도달했습니다. API 사용량과 잔액을 확인해 주세요.";
        if (status >= 500) return "OpenAI 서비스가 일시적으로 응답하지 않습니다. 잠시 후 다시 시도해 주세요.";
        return "AI 분석 요청을 처리하지 못했습니다. 입력 내용과 API 설정을 확인해 주세요.";
    }

    private String safeResponse(RestClientResponseException exception) {
        String response = exception.getResponseBodyAsString();
        return response.length() > 1000 ? response.substring(0, 1000) : response;
    }

    private Map<String, Object> responseSchema() {
        Map<String, Object> assessment = objectSchema(Map.of(
                "status", enumSchema("EVALUATED", "INSUFFICIENT_INFORMATION"),
                "score", nullableIntegerSchema(),
                "rationale", stringSchema(),
                "evidence", arraySchema(stringSchema())
        ), List.of("status", "score", "rationale", "evidence"));

        Map<String, Object> insight = objectSchema(Map.of(
                "title", stringSchema(),
                "description", stringSchema(),
                "userEvidence", stringSchema(),
                "jobEvidence", stringSchema()
        ), List.of("title", "description", "userEvidence", "jobEvidence"));

        Map<String, Object> jobInformation = objectSchema(Map.of(
                "responsibilities", arraySchema(stringSchema()),
                "requiredQualifications", arraySchema(stringSchema()),
                "preferredQualifications", arraySchema(stringSchema()),
                "employmentConditions", arraySchema(stringSchema()),
                "unknownInformation", arraySchema(stringSchema())
        ), List.of("responsibilities", "requiredQualifications", "preferredQualifications",
                "employmentConditions", "unknownInformation"));

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("companyName", stringSchema());
        properties.put("position", stringSchema());
        properties.put("summary", stringSchema());
        properties.put("skill", assessment);
        properties.put("experience", assessment);
        properties.put("role", assessment);
        properties.put("preference", assessment);
        properties.put("strengths", arraySchema(insight));
        properties.put("gaps", arraySchema(insight));
        properties.put("applicationTips", arraySchema(stringSchema()));
        properties.put("preparations", arraySchema(stringSchema()));
        properties.put("jobInformation", jobInformation);
        properties.put("dataLimitations", arraySchema(stringSchema()));

        return objectSchema(properties, new ArrayList<>(properties.keySet()));
    }

    private Map<String, Object> objectSchema(Map<String, Object> properties, List<String> required) {
        return Map.of("type", "object", "properties", properties, "required", required, "additionalProperties", false);
    }

    private Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    private Map<String, Object> enumSchema(String... values) {
        return Map.of("type", "string", "enum", List.of(values));
    }

    private Map<String, Object> arraySchema(Map<String, Object> items) {
        return Map.of("type", "array", "items", items);
    }

    private Map<String, Object> nullableIntegerSchema() {
        return Map.of("anyOf", List.of(
                Map.of("type", "integer", "minimum", 0, "maximum", 100),
                Map.of("type", "null")
        ));
    }

    public record AiResponse(
            String outputText,
            String model,
            int inputTokens,
            int outputTokens,
            int totalTokens,
            double estimatedCostUsd
    ) {
    }

    public static class AiAnalysisException extends RuntimeException {
        public AiAnalysisException(String message) {
            super(message);
        }

        public AiAnalysisException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
