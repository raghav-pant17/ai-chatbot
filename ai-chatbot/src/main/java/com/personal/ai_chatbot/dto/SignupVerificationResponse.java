package com.personal.ai_chatbot.dto;

public record SignupVerificationResponse(
        String email,
        boolean emailVerified,
        String message
) {
}
