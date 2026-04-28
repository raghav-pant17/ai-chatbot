package com.personal.ai_chatbot.dto;

import java.math.BigDecimal;

public record OrderItemResponse(
        String itemId,
        String name,
        BigDecimal price
) {
}
