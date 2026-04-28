package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.dto.AIEscalationResult;
import com.personal.ai_chatbot.dto.AIMessageAnalysis;
import com.personal.ai_chatbot.entity.ChatMessage;
import com.personal.ai_chatbot.enums.ConversationState;
import com.personal.ai_chatbot.enums.IssueType;
import com.personal.ai_chatbot.service.AIService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OpenAIService implements AIService {

    private final RuleBasedAIService fallback = new RuleBasedAIService();
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public OpenAIService(
            ObjectMapper objectMapper,
            @Value("${chatbot.ai.openai.api-key}") String apiKey,
            @Value("${chatbot.ai.openai.model}") String model,
            @Value("${chatbot.ai.openai.url}") String openAiUrl) {
        this.restClient = RestClient.builder().baseUrl(openAiUrl).build();
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
    }

    @Override
    public AIEscalationResult analyzeEscalation(List<ChatMessage> recentMessages, ConversationState state, String latestUserMessage) {
        return analyzeMessage(recentMessages, state, latestUserMessage)
                .map(AIMessageAnalysis::toEscalationResult)
                .orElseGet(() -> fallback.analyzeEscalation(recentMessages, state, latestUserMessage));
    }

    @Override
    public boolean isComplaintIntent(String message) {
        if (fallback.isComplaintIntent(message)) {
            return true;
        }
        return analyzeMessage(List.of(), ConversationState.START, message)
                .map(AIMessageAnalysis::hasComplaintIntent)
                .orElse(false);
    }

    @Override
    public Optional<String> extractOrderId(String message) {
        Optional<String> ruleBasedOrderId = fallback.extractOrderId(message);
        if (ruleBasedOrderId.isPresent()) {
            return ruleBasedOrderId;
        }
        return analyzeMessage(List.of(), ConversationState.ASK_ORDER_ID, message)
                .flatMap(AIMessageAnalysis::extractedOrderId);
    }

    @Override
    public Optional<IssueType> normalizeIssueType(String message) {
        Optional<IssueType> ruleBasedIssue = fallback.normalizeIssueType(message);
        if (ruleBasedIssue.isPresent()) {
            return ruleBasedIssue;
        }
        return analyzeMessage(List.of(), ConversationState.ASK_ISSUE, message)
                .flatMap(AIMessageAnalysis::normalizedIssueType);
    }

    private Optional<AIMessageAnalysis> analyzeMessage(List<ChatMessage> recentMessages, ConversationState state, String latestUserMessage) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        try {
            Map<String, Object> response = restClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequest(recentMessages, state, latestUserMessage))
                    .retrieve()
                    .body(Map.class);

            return parseResponse(response);
        } catch (RestClientException | IllegalArgumentException ex) {
            // AI is advisory only. If the API is unavailable, deterministic rules keep the chat flow alive.
            return Optional.empty();
        }
    }

    private Map<String, Object> buildRequest(List<ChatMessage> recentMessages, ConversationState state, String latestUserMessage) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("input", List.of(
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", buildUserPrompt(recentMessages, state, latestUserMessage))));
        request.put("text", Map.of("format", responseSchema()));
        return request;
    }

    private String systemPrompt() {
        return """
                You analyze customer support messages for an e-commerce complaint bot.
                Return only JSON matching the schema.
                Do not calculate refunds, update tickets, or decide final ticket state.
                Backend code validates every field and makes final decisions.
                """;
    }

    private String buildUserPrompt(List<ChatMessage> recentMessages, ConversationState state, String latestUserMessage) {
        StringBuilder builder = new StringBuilder();
        builder.append("Current state: ").append(state).append("\n");
        builder.append("Latest user message: ").append(latestUserMessage).append("\n");
        builder.append("Recent messages:\n");
        for (ChatMessage message : recentMessages) {
            builder.append(message.getSender()).append(": ").append(message.getMessage()).append("\n");
        }
        return builder.toString();
    }

    private Map<String, Object> responseSchema() {
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("intent", Map.of("type", "string", "enum", List.of("ORDER_COMPLAINT", "ORDER_QUERY", "REFUND_QUERY", "DELIVERY_QUERY", "OUT_OF_SCOPE", "OTHER")));
        properties.put("intentConfidence", Map.of("type", "number"));
        properties.put("orderId", nullableStringSchema());
        properties.put("issueType", nullableIssueTypeSchema());
        properties.put("issueConfidence", Map.of("type", "number"));
        properties.put("sentiment", Map.of("type", "string", "enum", List.of("NEUTRAL", "FRUSTRATED", "ANGRY", "DISSATISFIED")));
        properties.put("escalationRecommended", Map.of("type", "boolean"));
        properties.put("escalationConfidence", Map.of("type", "number"));
        properties.put("reason", Map.of("type", "string"));

        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("additionalProperties", false);
        schema.put("required", List.of("intent", "intentConfidence", "orderId", "issueType", "issueConfidence", "sentiment", "escalationRecommended", "escalationConfidence", "reason"));
        schema.put("properties", properties);

        return Map.of(
                "type", "json_schema",
                "name", "customer_support_ai_analysis",
                "strict", true,
                "schema", schema);
    }

    private Map<String, Object> nullableStringSchema() {
        return Map.of("anyOf", List.of(
                Map.of("type", "string"),
                Map.of("type", "null")));
    }

    private Map<String, Object> nullableIssueTypeSchema() {
        return Map.of("anyOf", List.of(
                Map.of("type", "string", "enum", List.of("DAMAGED", "NOT_DELIVERED", "WRONG_ITEM", "OTHER")),
                Map.of("type", "null")));
    }

    private Optional<AIMessageAnalysis> parseResponse(Map<String, Object> response) {
        String outputText = findOutputText(response);
        if (outputText == null || outputText.isBlank()) {
            return Optional.empty();
        }

        try {
            Map<String, Object> data = objectMapper.readValue(outputText, Map.class);
            return Optional.of(toAnalysis(data));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String findOutputText(Map<String, Object> response) {
        Object output = response == null ? null : response.get("output");
        if (!(output instanceof List<?> outputItems)) {
            return null;
        }
        for (Object outputItem : outputItems) {
            if (!(outputItem instanceof Map<?, ?> outputMap)) {
                continue;
            }
            Object content = outputMap.get("content");
            if (!(content instanceof List<?> contentItems)) {
                continue;
            }
            for (Object contentItem : contentItems) {
                if (contentItem instanceof Map<?, ?> contentMap && contentMap.get("text") instanceof String text) {
                    return text;
                }
            }
        }
        return null;
    }

    private AIMessageAnalysis toAnalysis(Map<String, Object> data) {
        return new AIMessageAnalysis(
                stringValue(data.get("intent"), "OTHER"),
                doubleValue(data.get("intentConfidence")),
                nullableString(data.get("orderId")),
                issueTypeValue(data.get("issueType")),
                doubleValue(data.get("issueConfidence")),
                stringValue(data.get("sentiment"), "NEUTRAL"),
                booleanValue(data.get("escalationRecommended")),
                doubleValue(data.get("escalationConfidence")),
                stringValue(data.get("reason"), "No reason provided"));
    }

    private String stringValue(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private String nullableString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private double doubleValue(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0.0;
    }

    private boolean booleanValue(Object value) {
        return value instanceof Boolean bool && bool;
    }

    private IssueType issueTypeValue(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return IssueType.valueOf(String.valueOf(value));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
