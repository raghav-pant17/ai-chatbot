package com.personal.ai_chatbot.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String userId
) {
}
