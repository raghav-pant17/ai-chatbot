package com.personal.ai_chatbot.dto;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

public record PendingSignup(
        String userId,
        String username,
        String fullName,
        String email,
        String passwordSalt,
        String passwordHash,
        BigDecimal shoppingBudget,
        String emailVerificationCodeHash,
        Instant emailVerificationExpiresAt
) implements Serializable {
}
