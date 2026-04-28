package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.dto.LoginRequest;
import com.personal.ai_chatbot.dto.LoginResponse;
import com.personal.ai_chatbot.dto.LogoutResponse;
import com.personal.ai_chatbot.entity.EcommerceUser;
import com.personal.ai_chatbot.repository.EcommerceUserRepository;
import com.personal.ai_chatbot.service.AuthTokenService;
import com.personal.ai_chatbot.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserServiceImpl implements UserService {

    private final EcommerceUserRepository ecommerceUserRepository;
    private final AuthTokenService authTokenService;

    public UserServiceImpl(EcommerceUserRepository ecommerceUserRepository, AuthTokenService authTokenService) {
        this.ecommerceUserRepository = ecommerceUserRepository;
        this.authTokenService = authTokenService;
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        EcommerceUser user = ecommerceUserRepository.findById(request.userId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User does not exist on the e-commerce platform."));

        String accessToken = authTokenService.createAccessToken(user.getUserId());
        return new LoginResponse(
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                accessToken,
                "Bearer",
                authTokenService.accessTokenExpirySeconds());
    }

    @Override
    public LogoutResponse logout(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        authTokenService.revokeAccessToken(token);
        return new LogoutResponse("Logged out successfully.");
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing bearer access token.");
        }
        return authorizationHeader.substring("Bearer ".length());
    }
}
