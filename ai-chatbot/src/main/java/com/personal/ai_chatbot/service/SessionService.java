package com.personal.ai_chatbot.service;

import com.personal.ai_chatbot.dto.UserSession;

import java.util.Optional;

public interface SessionService {

    Optional<UserSession> findByUserId(String userId);

    UserSession create(String userId);

    void save(UserSession session);
}
