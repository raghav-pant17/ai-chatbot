package com.personal.ai_chatbot.dto;

import com.personal.ai_chatbot.enums.ConversationState;
import com.personal.ai_chatbot.enums.TicketStatus;

public record AdminTicketActionResponse(
        Long ticketId,
        ConversationState state,
        TicketStatus status,
        String message
) {
}
