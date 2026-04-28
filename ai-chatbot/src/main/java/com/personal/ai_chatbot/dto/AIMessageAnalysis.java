package com.personal.ai_chatbot.dto;

import com.personal.ai_chatbot.enums.IssueType;

import java.util.Optional;

public record AIMessageAnalysis(
        String intent,
        double intentConfidence,
        String orderId,
        IssueType issueType,
        double issueConfidence,
        String sentiment,
        boolean escalationRecommended,
        double escalationConfidence,
        String reason
) {
    public static AIMessageAnalysis empty() {
        return new AIMessageAnalysis("OTHER", 0.0, null, null, 0.0, "NEUTRAL", false, 0.0, "No AI signal");
    }

    public boolean hasComplaintIntent() {
        return "ORDER_COMPLAINT".equalsIgnoreCase(intent) && intentConfidence >= 0.75;
    }

    public Optional<String> extractedOrderId() {
        return Optional.ofNullable(orderId).filter(value -> !value.isBlank());
    }

    public Optional<IssueType> normalizedIssueType() {
        if (issueConfidence < 0.75) {
            return Optional.empty();
        }
        return Optional.ofNullable(issueType);
    }

    public AIEscalationResult toEscalationResult() {
        return new AIEscalationResult(sentiment, escalationRecommended, escalationConfidence, reason);
    }
}
