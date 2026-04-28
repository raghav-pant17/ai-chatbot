package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.dto.AIEscalationResult;
import com.personal.ai_chatbot.entity.ChatMessage;
import com.personal.ai_chatbot.enums.ConversationState;
import com.personal.ai_chatbot.enums.IssueType;
import com.personal.ai_chatbot.service.AIService;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RuleBasedAIService implements AIService {

    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("[A-Za-z0-9-]{3,}");

    @Override
    public AIEscalationResult analyzeEscalation(List<ChatMessage> recentMessages, ConversationState state, String latestUserMessage) {
        String text = latestUserMessage == null ? "" : latestUserMessage.toLowerCase();
        if (text.contains("angry")
                || text.contains("frustrated")
                || text.contains("pissed")
                || text.contains("third time")
                || text.contains("nobody is helping")
                || text.contains("fuck")
                || text.contains("bullshit")) {
            return new AIEscalationResult("ANGRY", true, 0.92, "User expresses strong dissatisfaction");
        }
        if (text.contains("not happy") || text.contains("bad service") || text.contains("disappointed")) {
            return new AIEscalationResult("DISSATISFIED", true, 0.78, "User expresses dissatisfaction");
        }
        return AIEscalationResult.neutral();
    }

    @Override
    public boolean isComplaintIntent(String message) {
        String normalized = safeText(message);
        return normalized.contains("issue")
                || normalized.contains("problem")
                || normalized.contains("complaint")
                || normalized.contains("refund")
                || normalized.contains("damaged")
                || normalized.contains("broken")
                || normalized.contains("wrong item")
                || normalized.contains("not delivered");
    }

    @Override
    public Optional<String> extractOrderId(String message) {
        Matcher matcher = ORDER_ID_PATTERN.matcher(message == null ? "" : message.trim());
        while (matcher.find()) {
            String token = matcher.group();
            if (token.chars().anyMatch(Character::isDigit)) {
                return Optional.of(token);
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<IssueType> normalizeIssueType(String message) {
        String normalized = safeText(message);
        if (normalized.contains("damaged") || normalized.contains("broken")) {
            return Optional.of(IssueType.DAMAGED);
        }
        if (normalized.contains("not delivered") || normalized.contains("missing") || normalized.contains("not received")) {
            return Optional.of(IssueType.NOT_DELIVERED);
        }
        if (normalized.contains("wrong") || normalized.contains("different product")) {
            return Optional.of(IssueType.WRONG_ITEM);
        }
        if (normalized.contains("other")) {
            return Optional.of(IssueType.OTHER);
        }
        return Optional.empty();
    }

    private String safeText(String message) {
        return message == null ? "" : message.toLowerCase();
    }
}
