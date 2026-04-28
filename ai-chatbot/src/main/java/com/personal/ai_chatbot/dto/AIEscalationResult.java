package com.personal.ai_chatbot.dto;

public record AIEscalationResult(
        String sentiment,
        boolean escalationRecommended,
        double confidence,
        String reason
) {
    public static AIEscalationResult neutral() {
        return new AIEscalationResult("NEUTRAL", false, 0.0, "No escalation signal");
    }
}
