package com.personal.ai_chatbot.dto;

import java.math.BigDecimal;

public record BudgetResponse(
        BigDecimal shoppingBudget,
        BigDecimal spentAmount,
        BigDecimal remainingBudget
) {
}
