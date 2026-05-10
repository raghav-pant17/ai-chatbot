package com.personal.ai_chatbot.dto;

import java.math.BigDecimal;

public record ProductResponse(
        String productId,
        String name,
        String category,
        BigDecimal price
) {
}
