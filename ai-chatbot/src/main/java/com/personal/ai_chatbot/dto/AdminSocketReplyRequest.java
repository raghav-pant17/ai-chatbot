package com.personal.ai_chatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AdminSocketReplyRequest(
        @NotNull Long ticketId,
        @NotBlank String message
) {
}
