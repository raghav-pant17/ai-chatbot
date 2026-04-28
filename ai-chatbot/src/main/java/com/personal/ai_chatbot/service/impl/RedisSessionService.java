package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.dto.UserSession;
import com.personal.ai_chatbot.service.SessionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RedisSessionService implements SessionService {

    private static final String SESSION_KEY_PREFIX = "session:";

    private final RedisTemplate<String, UserSession> redisTemplate;
    private final Duration sessionTtl;
    private final Map<String, UserSession> localFallbackSessions = new ConcurrentHashMap<>();

    public RedisSessionService(
            RedisTemplate<String, UserSession> redisTemplate,
            @Value("${chatbot.session-ttl-minutes}") long sessionTtlMinutes) {
        this.redisTemplate = redisTemplate;
        this.sessionTtl = Duration.ofMinutes(sessionTtlMinutes);
    }

    @Override
    public Optional<UserSession> findByUserId(String userId) {
        try {
            UserSession session = redisTemplate.opsForValue().get(buildKey(userId));
            if (session != null) {
                localFallbackSessions.put(userId, session);
                return Optional.of(session);
            }
        } catch (RedisConnectionFailureException ex) {
            // Fall through to the local cache so the app remains usable while Redis is down.
        }
        return Optional.ofNullable(localFallbackSessions.get(userId));
    }

    @Override
    public UserSession create(String userId) {
        UserSession session = new UserSession();
        session.setUserId(userId);
        save(session);
        return session;
    }

    @Override
    public void save(UserSession session) {
        localFallbackSessions.put(session.getUserId(), session);
        try {
            redisTemplate.opsForValue().set(buildKey(session.getUserId()), session, sessionTtl);
        } catch (DataAccessException ex) {
            // Local development can continue without Redis; PostgreSQL still holds the ticket truth.
        }
    }

    private String buildKey(String userId) {
        return SESSION_KEY_PREFIX + userId;
    }
}
