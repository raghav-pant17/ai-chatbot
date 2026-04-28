package com.personal.ai_chatbot.dto;

import com.personal.ai_chatbot.enums.UserRole;

import java.time.Instant;

public record AuthenticatedUser(
        String userId,
        UserRole role,
        String tokenId,
        Instant expiresAt
) {
}
