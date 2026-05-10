package com.personal.ai_chatbot.dto;

public record CreateOrderResponse(
        OrderDetailResponse order,
        BudgetResponse budget
) {
}
