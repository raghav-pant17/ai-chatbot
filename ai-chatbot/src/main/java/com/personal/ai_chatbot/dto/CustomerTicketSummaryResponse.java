package com.personal.ai_chatbot.dto;

import com.personal.ai_chatbot.enums.ConversationState;
import com.personal.ai_chatbot.enums.IssueType;
import com.personal.ai_chatbot.enums.TicketStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record CustomerTicketSummaryResponse(
        Long ticketId,
        String orderId,
        IssueType issueType,
        ConversationState state,
        TicketStatus status,
        BigDecimal refundAmount,
        int itemCount,
        Instant createdAt,
        Instant updatedAt
) {
}
