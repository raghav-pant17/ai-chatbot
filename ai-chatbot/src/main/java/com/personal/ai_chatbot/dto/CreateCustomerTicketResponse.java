package com.personal.ai_chatbot.dto;

public record CreateCustomerTicketResponse(
        CustomerTicketSummaryResponse ticket,
        String message
) {
}
