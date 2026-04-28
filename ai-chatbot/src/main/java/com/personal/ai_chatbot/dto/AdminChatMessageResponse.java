package com.personal.ai_chatbot.dto;

import com.personal.ai_chatbot.enums.MessageSender;

import java.time.Instant;

public record AdminChatMessageResponse(
        Long id,
        MessageSender sender,
        String message,
        Instant timestamp
) {
}
