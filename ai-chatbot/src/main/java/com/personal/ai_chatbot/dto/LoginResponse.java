package com.personal.ai_chatbot.dto;

public record LoginResponse(
        String userId,
        String fullName,
        String email,
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
}
