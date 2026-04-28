package com.personal.ai_chatbot.dto;

import jakarta.validation.constraints.NotBlank;

public record ChatMessageRequest(
        @NotBlank String userId,
        @NotBlank String message
) {
}
