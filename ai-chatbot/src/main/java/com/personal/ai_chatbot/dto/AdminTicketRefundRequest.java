package com.personal.ai_chatbot.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record AdminTicketRefundRequest(
        @NotNull @DecimalMin(value = "0.0", inclusive = false) BigDecimal amount
) {
}
