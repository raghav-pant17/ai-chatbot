package com.personal.ai_chatbot.config;

import com.personal.ai_chatbot.dto.AuthenticatedUser;
import com.personal.ai_chatbot.enums.UserRole;
import com.personal.ai_chatbot.service.AuthTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AuthInterceptor implements HandlerInterceptor {

    public static final String AUTHENTICATED_USER_ID = "authenticatedUserId";

    private final AuthTokenService authTokenService;

    public AuthInterceptor(AuthTokenService authTokenService) {
        this.authTokenService = authTokenService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = extractBearerToken(request.getHeader(HttpHeaders.AUTHORIZATION));
        if (token == null) {
            response.sendError(HttpStatus.UNAUTHORIZED.value(), "Missing bearer access token.");
            return false;
        }

        AuthenticatedUser authenticatedUser;
        try {
            authenticatedUser = authTokenService.validateAccessToken(token);
        } catch (ResponseStatusException ex) {
            response.sendError(ex.getStatusCode().value(), ex.getReason());
            return false;
        }

        if (isAdminPath(request) && authenticatedUser.role() != UserRole.ADMIN) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "Admin access token is required.");
            return false;
        }

        if (isCustomerPath(request) && authenticatedUser.role() != UserRole.CUSTOMER) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "Customer access token is required.");
            return false;
        }

        String pathUserId = extractUserIdFromPath(request);
        if (pathUserId != null && !pathUserId.equals(authenticatedUser.userId())) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "Access token does not match requested user.");
            return false;
        }

        request.setAttribute(AUTHENTICATED_USER_ID, authenticatedUser.userId());
        return true;
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || !authorizationHeader.startsWith("Bearer ")) {
            return null;
        }
        return authorizationHeader.substring("Bearer ".length());
    }

    private String extractUserIdFromPath(HttpServletRequest request) {
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        String path = request.getRequestURI().substring(contextPath.length());
        String[] parts = path.split("/");
        if (parts.length >= 4 && "api".equals(parts[1]) && "users".equals(parts[2])) {
            return parts[3];
        }
        return null;
    }

    private boolean isAdminPath(HttpServletRequest request) {
        return normalizedPath(request).startsWith("/api/admin/");
    }

    private boolean isCustomerPath(HttpServletRequest request) {
        return normalizedPath(request).startsWith("/api/users/");
    }

    private String normalizedPath(HttpServletRequest request) {
        String contextPath = request.getContextPath() == null ? "" : request.getContextPath();
        return request.getRequestURI().substring(contextPath.length());
    }
}
