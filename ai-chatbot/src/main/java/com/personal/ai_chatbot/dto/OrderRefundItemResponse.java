package com.personal.ai_chatbot.dto;

public record OrderRefundItemResponse(
        Integer itemNumber,
        String itemId,
        String name
) {
}
