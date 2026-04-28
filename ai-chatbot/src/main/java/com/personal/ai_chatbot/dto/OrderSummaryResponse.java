package com.personal.ai_chatbot.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderSummaryResponse(
        String orderId,
        BigDecimal totalAmount,
        Instant orderTime,
        int itemCount
) {
}
