package com.personal.ai_chatbot.service;

import com.personal.ai_chatbot.dto.AuthenticatedUser;
import com.personal.ai_chatbot.enums.UserRole;

public interface AuthTokenService {

    default String createAccessToken(String userId) {
        return createAccessToken(userId, UserRole.CUSTOMER);
    }

    String createAccessToken(String subject, UserRole role);

    AuthenticatedUser validateAccessToken(String token);

    void revokeAccessToken(String token);

    long accessTokenExpirySeconds();
}
