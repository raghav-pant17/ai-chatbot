package com.personal.ai_chatbot.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDetailResponse(
        String orderId,
        String userId,
        BigDecimal totalAmount,
        BigDecimal refundAmount,
        List<OrderRefundItemResponse> refundItems,
        Instant orderTime,
        List<OrderItemResponse> items
) {
}
