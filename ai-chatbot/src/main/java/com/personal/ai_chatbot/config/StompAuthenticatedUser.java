package com.personal.ai_chatbot.config;

import com.personal.ai_chatbot.dto.AuthenticatedUser;
import com.personal.ai_chatbot.enums.UserRole;

import java.security.Principal;

public class StompAuthenticatedUser implements Principal {

    private final AuthenticatedUser authenticatedUser;

    public StompAuthenticatedUser(AuthenticatedUser authenticatedUser) {
        this.authenticatedUser = authenticatedUser;
    }

    @Override
    public String getName() {
        return authenticatedUser.userId();
    }

    public UserRole role() {
        return authenticatedUser.role();
    }
}
