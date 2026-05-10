package com.personal.ai_chatbot.config;

import com.personal.ai_chatbot.dto.AuthenticatedUser;
import com.personal.ai_chatbot.enums.UserRole;
import com.personal.ai_chatbot.service.AuthTokenService;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;

@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private final AuthTokenService authTokenService;

    public WebSocketAuthInterceptor(AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            AuthenticatedUser authenticatedUser = authTokenService.validateAccessToken(extractBearerToken(accessor));
            accessor.setUser(new StompAuthenticatedUser(authenticatedUser));
            return message;
        }

        if (StompCommand.SEND.equals(accessor.getCommand())) {
            enforceSendPermission(accessor);
        }

        return message;
    }

    private String extractBearerToken(StompHeaderAccessor accessor) {
        String authorization = accessor.getFirstNativeHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new IllegalArgumentException("Missing WebSocket bearer access token.");
        }
        return authorization.substring("Bearer ".length());
    }

    private void enforceSendPermission(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        Principal user = accessor.getUser();
        if (destination == null || user == null) {
            throw new IllegalArgumentException("Authenticated WebSocket user is required.");
        }

        UserRole role = user instanceof StompAuthenticatedUser stompUser ? stompUser.role() : null;
        if ("/app/chat.sendMessage".equals(destination) && role != UserRole.CUSTOMER) {
            throw new IllegalArgumentException("Customer token is required.");
        }
        if ("/app/admin.reply".equals(destination) && role != UserRole.ADMIN) {
            throw new IllegalArgumentException("Admin token is required.");
        }
    }
}
