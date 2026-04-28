package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.dto.AuthenticatedUser;
import com.personal.ai_chatbot.enums.UserRole;
import com.personal.ai_chatbot.service.AuthTokenService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class JwtAuthTokenService implements AuthTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    private final ObjectMapper objectMapper;
    private final String jwtSecret;
    private final long accessTokenExpirySeconds;
    private final Map<String, Instant> revokedTokenIds = new ConcurrentHashMap<>();

    public JwtAuthTokenService(
            ObjectMapper objectMapper,
            @Value("${chatbot.auth.jwt-secret}") String jwtSecret,
            @Value("${chatbot.auth.access-token-expiry-minutes}") long accessTokenExpiryMinutes) {
        this.objectMapper = objectMapper;
        this.jwtSecret = jwtSecret;
        this.accessTokenExpirySeconds = accessTokenExpiryMinutes * 60;
    }

    @Override
    public String createAccessToken(String subject, UserRole role) {
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(accessTokenExpirySeconds);
        String tokenId = UUID.randomUUID().toString();

        Map<String, Object> header = Map.of(
                "alg", "HS256",
                "typ", "JWT");

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", subject);
        payload.put("role", role.name());
        payload.put("jti", tokenId);
        payload.put("iat", now.getEpochSecond());
        payload.put("exp", expiresAt.getEpochSecond());

        String encodedHeader = base64Url(json(header));
        String encodedPayload = base64Url(json(payload));
        String unsignedToken = encodedHeader + "." + encodedPayload;
        // The signature protects the user ID and expiry from client-side changes.
        return unsignedToken + "." + sign(unsignedToken);
    }

    @Override
    public AuthenticatedUser validateAccessToken(String token) {
        String[] parts = token == null ? new String[0] : token.split("\\.");
        if (parts.length != 3) {
            throw unauthorized("Invalid access token.");
        }

        String unsignedToken = parts[0] + "." + parts[1];
        String expectedSignature = sign(unsignedToken);
        if (!constantTimeEquals(expectedSignature, parts[2])) {
            throw unauthorized("Invalid access token signature.");
        }

        Map<String, Object> payload = parsePayload(parts[1]);
        String userId = stringClaim(payload, "sub");
        UserRole role = roleClaim(payload);
        String tokenId = stringClaim(payload, "jti");
        Instant expiresAt = Instant.ofEpochSecond(longClaim(payload, "exp"));

        if (expiresAt.isBefore(Instant.now())) {
            throw unauthorized("Access token has expired.");
        }
        if (revokedTokenIds.containsKey(tokenId)) {
            throw unauthorized("Access token has been logged out.");
        }

        return new AuthenticatedUser(userId, role, tokenId, expiresAt);
    }

    @Override
    public void revokeAccessToken(String token) {
        AuthenticatedUser authenticatedUser = validateAccessToken(token);
        revokedTokenIds.put(authenticatedUser.tokenId(), authenticatedUser.expiresAt());
        removeExpiredRevokedTokens();
    }

    @Override
    public long accessTokenExpirySeconds() {
        return accessTokenExpirySeconds;
    }

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create access token.", ex);
        }
    }

    private Map<String, Object> parsePayload(String encodedPayload) {
        try {
            byte[] payloadBytes = Base64.getUrlDecoder().decode(encodedPayload);
            return objectMapper.readValue(new String(payloadBytes, StandardCharsets.UTF_8), Map.class);
        } catch (Exception ex) {
            throw unauthorized("Invalid access token payload.");
        }
    }

    private String sign(String unsignedToken) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            SecretKeySpec key = new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM);
            mac.init(key);
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(unsignedToken.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign access token.", ex);
        }
    }

    private String base64Url(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
        if (leftBytes.length != rightBytes.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < leftBytes.length; i++) {
            result |= leftBytes[i] ^ rightBytes[i];
        }
        return result == 0;
    }

    private String stringClaim(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value == null || String.valueOf(value).isBlank()) {
            throw unauthorized("Missing access token claim: " + key);
        }
        return String.valueOf(value);
    }

    private long longClaim(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw unauthorized("Missing access token claim: " + key);
    }

    private UserRole roleClaim(Map<String, Object> payload) {
        try {
            return UserRole.valueOf(stringClaim(payload, "role"));
        } catch (IllegalArgumentException ex) {
            throw unauthorized("Invalid access token role.");
        }
    }

    private ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }

    private void removeExpiredRevokedTokens() {
        Instant now = Instant.now();
        // Revoked JWT IDs only need to stay until the original token expiry.
        revokedTokenIds.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }
}
