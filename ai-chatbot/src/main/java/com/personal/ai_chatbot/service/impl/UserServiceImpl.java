package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.dto.LoginRequest;
import com.personal.ai_chatbot.dto.LoginResponse;
import com.personal.ai_chatbot.dto.LogoutResponse;
import com.personal.ai_chatbot.dto.ResendVerificationRequest;
import com.personal.ai_chatbot.dto.SignupRequest;
import com.personal.ai_chatbot.dto.SignupVerificationResponse;
import com.personal.ai_chatbot.dto.VerifyEmailRequest;
import com.personal.ai_chatbot.entity.EcommerceUser;
import com.personal.ai_chatbot.repository.CustomerOrderRepository;
import com.personal.ai_chatbot.repository.EcommerceUserRepository;
import com.personal.ai_chatbot.service.AuthTokenService;
import com.personal.ai_chatbot.service.EmailService;
import com.personal.ai_chatbot.service.UserService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class UserServiceImpl implements UserService {

    private static final BigDecimal DEFAULT_SHOPPING_BUDGET = new BigDecimal("15000.00");

    private final EcommerceUserRepository ecommerceUserRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final AuthTokenService authTokenService;
    private final EmailService emailService;
    private final Duration verificationExpiry;
    private final SecureRandom secureRandom = new SecureRandom();

    public UserServiceImpl(
            EcommerceUserRepository ecommerceUserRepository,
            CustomerOrderRepository customerOrderRepository,
            AuthTokenService authTokenService,
            EmailService emailService,
            @Value("${chatbot.auth.email-verification-expiry-minutes}") long verificationExpiryMinutes) {
        this.ecommerceUserRepository = ecommerceUserRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.authTokenService = authTokenService;
        this.emailService = emailService;
        this.verificationExpiry = Duration.ofMinutes(verificationExpiryMinutes);
    }

    @Override
    @Transactional
    public SignupVerificationResponse signup(SignupRequest request) {
        String username = request.username().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        if (ecommerceUserRepository.existsByUsername(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already in use.");
        }
        if (ecommerceUserRepository.existsByEmail(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use.");
        }

        String salt = newSalt();
        EcommerceUser user = new EcommerceUser();
        user.setUserId("user-" + UUID.randomUUID());
        user.setUsername(username);
        user.setFullName(request.fullName().trim());
        user.setEmail(email);
        user.setPasswordSalt(salt);
        user.setPasswordHash(sha256(salt + ":" + request.password()));
        user.setShoppingBudget(DEFAULT_SHOPPING_BUDGET);
        user.setEmailVerified(false);

        issueVerificationCode(user);
        EcommerceUser savedUser = ecommerceUserRepository.save(user);
        return new SignupVerificationResponse(
                savedUser.getEmail(),
                savedUser.isEmailVerified(),
                "Verification code sent to " + savedUser.getEmail() + ".");
    }

    @Override
    @Transactional
    public LoginResponse verifyEmail(VerifyEmailRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        EcommerceUser user = ecommerceUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification request was not found."));

        if (user.isEmailVerified()) {
            return buildLoginResponse(user);
        }

        if (user.getEmailVerificationExpiresAt() == null || Instant.now().isAfter(user.getEmailVerificationExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification code expired. Please request a new code.");
        }

        String candidateHash = sha256(user.getPasswordSalt() + ":email:" + request.code());
        if (!MessageDigest.isEqual(
                candidateHash.getBytes(StandardCharsets.UTF_8),
                user.getEmailVerificationCodeHash().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification code.");
        }

        user.setEmailVerified(true);
        user.setEmailVerificationCodeHash(null);
        user.setEmailVerificationExpiresAt(null);
        return buildLoginResponse(ecommerceUserRepository.save(user));
    }

    @Override
    @Transactional
    public SignupVerificationResponse resendVerificationCode(ResendVerificationRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        EcommerceUser user = ecommerceUserRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification request was not found."));

        if (user.isEmailVerified()) {
            return new SignupVerificationResponse(user.getEmail(), true, "Email is already verified.");
        }

        issueVerificationCode(user);
        ecommerceUserRepository.save(user);
        return new SignupVerificationResponse(user.getEmail(), false, "A new verification code was sent.");
    }

    @Override
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        EcommerceUser user = ecommerceUserRepository.findByUsername(request.username())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password."));

        if (!passwordMatches(request.password(), user)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid username or password.");
        }

        if (!user.isEmailVerified()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Please verify your email before logging in.");
        }

        return buildLoginResponse(user);
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

    private boolean passwordMatches(String rawPassword, EcommerceUser user) {
        String candidateHash = sha256(user.getPasswordSalt() + ":" + rawPassword);
        return MessageDigest.isEqual(
                candidateHash.getBytes(StandardCharsets.UTF_8),
                user.getPasswordHash().getBytes(StandardCharsets.UTF_8));
    }

    private LoginResponse buildLoginResponse(EcommerceUser user) {
        BigDecimal spentAmount = customerOrderRepository.sumTotalAmountByUserId(user.getUserId());
        BigDecimal shoppingBudget = user.getShoppingBudget() == null ? DEFAULT_SHOPPING_BUDGET : user.getShoppingBudget();
        BigDecimal remainingBudget = shoppingBudget.subtract(spentAmount == null ? BigDecimal.ZERO : spentAmount);
        String accessToken = authTokenService.createAccessToken(user.getUserId());
        return new LoginResponse(
                user.getUserId(),
                user.getFullName(),
                user.getEmail(),
                shoppingBudget,
                spentAmount == null ? BigDecimal.ZERO : spentAmount,
                remainingBudget.max(BigDecimal.ZERO),
                accessToken,
                "Bearer",
                authTokenService.accessTokenExpirySeconds());
    }

    private String newSalt() {
        byte[] bytes = new byte[18];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void issueVerificationCode(EcommerceUser user) {
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        user.setEmailVerificationCodeHash(sha256(user.getPasswordSalt() + ":email:" + code));
        user.setEmailVerificationExpiresAt(Instant.now().plus(verificationExpiry));
        emailService.sendVerificationCode(user.getEmail(), code);
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to verify customer password.", ex);
        }
    }
}
