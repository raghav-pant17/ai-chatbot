package com.personal.ai_chatbot.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record OrderLineRequest(
        @NotBlank String productId,
        @Min(1) int quantity
) {
}
