package com.personal.ai_chatbot.dto;

import jakarta.validation.constraints.NotBlank;

public record AdminTicketReplyRequest(
        @NotBlank String message
) {
}
