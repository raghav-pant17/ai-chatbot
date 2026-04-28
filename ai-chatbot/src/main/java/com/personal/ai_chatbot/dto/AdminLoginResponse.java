package com.personal.ai_chatbot.dto;

public record AdminLoginResponse(
        String adminId,
        String fullName,
        String accessToken,
        String tokenType,
        long expiresInSeconds
) {
}
