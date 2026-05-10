package com.personal.ai_chatbot.dto;

public record LoginResponse(
        String userId,
        String fullName,
        String email,
        java.math.BigDecimal shoppingBudget,
        java.math.BigDecimal spentAmount,
        java.math.BigDecimal remainingBudget,
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
}
