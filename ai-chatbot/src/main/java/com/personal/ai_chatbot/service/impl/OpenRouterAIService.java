package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.dto.AIEscalationResult;
import com.personal.ai_chatbot.dto.AIMessageAnalysis;
import com.personal.ai_chatbot.entity.ChatMessage;
import com.personal.ai_chatbot.enums.ConversationState;
import com.personal.ai_chatbot.enums.IssueType;
import com.personal.ai_chatbot.service.AIService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class OpenRouterAIService implements AIService {

    private static final Logger log = LoggerFactory.getLogger(OpenRouterAIService.class);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(20);

    private final RuleBasedAIService fallback = new RuleBasedAIService();
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String model;

    public OpenRouterAIService(
            ObjectMapper objectMapper,
            @Value("${chatbot.ai.openrouter.api-key}") String apiKey,
            @Value("${chatbot.ai.openrouter.model}") String model,
            @Value("${chatbot.ai.openrouter.url}") String openRouterUrl) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(openRouterUrl)
                .requestFactory(requestFactory)
                .build();
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
            log.info("OpenRouter analysis skipped because API key is blank state={} messageLength={}", state, latestUserMessage == null ? 0 : latestUserMessage.length());
            return Optional.empty();
        }

        long startedAt = System.nanoTime();
        log.info("OpenRouter analysis request started model={} state={} recentMessages={} messageLength={}", model, state, recentMessages.size(), latestUserMessage == null ? 0 : latestUserMessage.length());
        try {
            Map<String, Object> response = restClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .header("X-Title", "chatbot-ai")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(buildRequest(recentMessages, state, latestUserMessage))
                    .retrieve()
                    .body(Map.class);

            Optional<AIMessageAnalysis> analysis = parseResponse(response);
            log.info("OpenRouter analysis request completed elapsedMs={} parsed={}", elapsedMillis(startedAt), analysis.isPresent());
            return analysis;
        } catch (RestClientResponseException ex) {
            log.warn("OpenRouter analysis request failed with HTTP {} elapsedMs={}", ex.getStatusCode().value(), elapsedMillis(startedAt));
            return Optional.empty();
        } catch (RestClientException | IllegalArgumentException ex) {
            log.warn("OpenRouter analysis request failed elapsedMs={} error={}", elapsedMillis(startedAt), ex.getMessage());
            return Optional.empty();
        }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }

    private Map<String, Object> buildRequest(List<ChatMessage> recentMessages, ConversationState state, String latestUserMessage) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", model);
        request.put("temperature", 0);
        request.put("messages", List.of(
                Map.of("role", "system", "content", systemPrompt()),
                Map.of("role", "user", "content", buildUserPrompt(recentMessages, state, latestUserMessage))));
        return request;
    }

    private String systemPrompt() {
        return """
                You analyze customer support messages for an e-commerce complaint bot.
                Return only valid JSON. Do not wrap it in markdown.
                Required fields:
                intent, intentConfidence, orderId, issueType, issueConfidence,
                sentiment, escalationRecommended, escalationConfidence, reason.
                The intent value must be exactly one of:
                ORDER_COMPLAINT, ORDER_QUERY, REFUND_QUERY, DELIVERY_QUERY, OUT_OF_SCOPE, OTHER.
                Valid issueType values are DAMAGED, NOT_DELIVERED, WRONG_ITEM, OTHER, or null.
                The sentiment value must be exactly one of:
                NEUTRAL, FRUSTRATED, ANGRY, DISSATISFIED.
                Escalation policy:
                - If the customer asks for a human, agent, manager, representative, support person, or any non-bot help, set escalationRecommended=true and escalationConfidence >= 0.90.
                - If the customer insults the bot, refuses bot help, or expresses strong anger/frustration, set escalationRecommended=true and escalationConfidence >= 0.85.
                - Escalation can happen in any conversation state, including START and ASK_ORDER_ID.
                - Missing orderId must never prevent escalation. If escalation is needed but no order ID is present, set orderId=null.
                - Do not escalate only because the customer has a refund, delivery, damaged item, or order complaint.
                - Do not escalate when the customer is simply providing an order ID or answering the bot's normal form-like prompts.
                - If escalation is not needed, set escalationRecommended=false and use a low escalationConfidence.
                Example:
                {"intent":"ORDER_COMPLAINT","intentConfidence":0.95,"orderId":"ORD-1001","issueType":"DAMAGED","issueConfidence":0.95,"sentiment":"FRUSTRATED","escalationRecommended":false,"escalationConfidence":0.2,"reason":"Customer reported damaged items for a known order."}
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

    private Optional<AIMessageAnalysis> parseResponse(Map<String, Object> response) {
        String content = findMessageContent(response);
        if (content == null || content.isBlank()) {
            return Optional.empty();
        }

        try {
            Map<String, Object> data = objectMapper.readValue(stripCodeFence(content), Map.class);
            return Optional.of(toAnalysis(data));
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private String findMessageContent(Map<String, Object> response) {
        Object choices = response == null ? null : response.get("choices");
        if (!(choices instanceof List<?> choiceItems) || choiceItems.isEmpty()) {
            return null;
        }
        Object firstChoice = choiceItems.get(0);
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            return null;
        }
        Object message = choiceMap.get("message");
        if (message instanceof Map<?, ?> messageMap && messageMap.get("content") instanceof String content) {
            return content;
        }
        return null;
    }

    private String stripCodeFence(String content) {
        String trimmed = content.trim();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        int lastFence = trimmed.lastIndexOf("```");
        if (firstNewline >= 0 && lastFence > firstNewline) {
            return trimmed.substring(firstNewline + 1, lastFence).trim();
        }
        return trimmed;
    }

    private AIMessageAnalysis toAnalysis(Map<String, Object> data) {
        return new AIMessageAnalysis(
                intentValue(data.get("intent")),
                doubleValue(data.get("intentConfidence")),
                nullableString(data.get("orderId")),
                issueTypeValue(data.get("issueType")),
                doubleValue(data.get("issueConfidence")),
                sentimentValue(data.get("sentiment")),
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

    private String intentValue(Object value) {
        if (value == null) {
            return "OTHER";
        }

        String normalized = String.valueOf(value).trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "ORDER_COMPLAINT", "COMPLAINT", "DAMAGE_REPORT", "DAMAGED_ORDER", "REPORT_DAMAGE", "REFUND_COMPLAINT" -> "ORDER_COMPLAINT";
            case "ORDER_QUERY", "ORDER_STATUS", "ORDER_LOOKUP" -> "ORDER_QUERY";
            case "REFUND_QUERY", "REFUND_REQUEST", "REFUND" -> "REFUND_QUERY";
            case "DELIVERY_QUERY", "DELIVERY_ISSUE", "SHIPPING_QUERY" -> "DELIVERY_QUERY";
            case "OUT_OF_SCOPE" -> "OUT_OF_SCOPE";
            default -> "OTHER";
        };
    }

    private String sentimentValue(Object value) {
        if (value == null) {
            return "NEUTRAL";
        }

        String normalized = String.valueOf(value).trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "ANGRY", "FURIOUS", "IRATE" -> "ANGRY";
            case "DISSATISFIED", "UNHAPPY", "NEGATIVE" -> "DISSATISFIED";
            case "FRUSTRATED", "UPSET", "ANNOYED" -> "FRUSTRATED";
            default -> "NEUTRAL";
        };
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
