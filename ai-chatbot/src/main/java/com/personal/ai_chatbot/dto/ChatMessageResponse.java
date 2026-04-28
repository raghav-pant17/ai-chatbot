package com.personal.ai_chatbot.dto;

import com.personal.ai_chatbot.enums.ConversationState;
import com.personal.ai_chatbot.enums.MessageSender;
import com.personal.ai_chatbot.enums.TicketStatus;

import java.time.Instant;

public record ChatMessageResponse(
        String userId,
        Long ticketId,
        String orderId,
        ConversationState state,
        TicketStatus status,
        String message,
        Instant timestamp,
        MessageSender sender,
        boolean persistMessage
) {
    public ChatMessageResponse(
            String userId,
            Long ticketId,
            ConversationState state,
            TicketStatus status,
            String message,
            Instant timestamp) {
        this(userId, ticketId, null, state, status, message, timestamp, MessageSender.BOT, true);
    }

    public ChatMessageResponse(
            String userId,
            Long ticketId,
            String orderId,
            ConversationState state,
            TicketStatus status,
            String message,
            Instant timestamp) {
        this(userId, ticketId, orderId, state, status, message, timestamp, MessageSender.BOT, true);
    }

    public ChatMessageResponse(
            String userId,
            Long ticketId,
            ConversationState state,
            TicketStatus status,
            String message,
            Instant timestamp,
            boolean persistMessage) {
        this(userId, ticketId, null, state, status, message, timestamp, MessageSender.BOT, persistMessage);
    }

    public ChatMessageResponse(
            String userId,
            Long ticketId,
            String orderId,
            ConversationState state,
            TicketStatus status,
            String message,
            Instant timestamp,
            boolean persistMessage) {
        this(userId, ticketId, orderId, state, status, message, timestamp, MessageSender.BOT, persistMessage);
    }

    public ChatMessageResponse(
            String userId,
            Long ticketId,
            ConversationState state,
            TicketStatus status,
            String message,
            Instant timestamp,
            MessageSender sender,
            boolean persistMessage) {
        this(userId, ticketId, null, state, status, message, timestamp, sender, persistMessage);
    }
}
