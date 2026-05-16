package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.dto.LoginRequest;
import com.personal.ai_chatbot.dto.LoginResponse;
import com.personal.ai_chatbot.dto.LogoutResponse;
import com.personal.ai_chatbot.dto.PendingSignup;
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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.RedisTemplate;
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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class UserServiceImpl implements UserService {

    private static final BigDecimal DEFAULT_SHOPPING_BUDGET = new BigDecimal("15000.00");
    private static final String PENDING_SIGNUP_KEY_PREFIX = "pending_signup:";

    private final EcommerceUserRepository ecommerceUserRepository;
    private final CustomerOrderRepository customerOrderRepository;
    private final AuthTokenService authTokenService;
    private final EmailService emailService;
    private final RedisTemplate<String, PendingSignup> pendingSignupRedisTemplate;
    private final Duration verificationExpiry;
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, PendingSignup> localPendingSignups = new ConcurrentHashMap<>();

    public UserServiceImpl(
            EcommerceUserRepository ecommerceUserRepository,
            CustomerOrderRepository customerOrderRepository,
            AuthTokenService authTokenService,
            EmailService emailService,
            @Qualifier("pendingSignupRedisTemplate") RedisTemplate<String, PendingSignup> pendingSignupRedisTemplate,
            @Value("${chatbot.auth.email-verification-expiry-minutes}") long verificationExpiryMinutes) {
        this.ecommerceUserRepository = ecommerceUserRepository;
        this.customerOrderRepository = customerOrderRepository;
        this.authTokenService = authTokenService;
        this.emailService = emailService;
        this.pendingSignupRedisTemplate = pendingSignupRedisTemplate;
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
        VerificationCode verificationCode = newVerificationCode(salt);
        PendingSignup pendingSignup = new PendingSignup(
                "user-" + UUID.randomUUID(),
                username,
                request.fullName().trim(),
                email,
                salt,
                sha256(salt + ":" + request.password()),
                DEFAULT_SHOPPING_BUDGET,
                verificationCode.hash(),
                verificationCode.expiresAt());

        savePendingSignup(pendingSignup);
        emailService.sendVerificationCode(email, verificationCode.code());
        return new SignupVerificationResponse(
                pendingSignup.email(),
                false,
                "Verification code sent to " + pendingSignup.email() + ".");
    }

    @Override
    @Transactional
    public LoginResponse verifyEmail(VerifyEmailRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        PendingSignup pendingSignup = findPendingSignup(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification request was not found."));

        if (Instant.now().isAfter(pendingSignup.emailVerificationExpiresAt())) {
            deletePendingSignup(email);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Verification code expired. Please request a new code.");
        }

        String candidateHash = sha256(pendingSignup.passwordSalt() + ":email:" + request.code());
        if (!MessageDigest.isEqual(
                candidateHash.getBytes(StandardCharsets.UTF_8),
                pendingSignup.emailVerificationCodeHash().getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification code.");
        }

        if (ecommerceUserRepository.existsByUsername(pendingSignup.username())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already in use.");
        }
        if (ecommerceUserRepository.existsByEmail(pendingSignup.email())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use.");
        }

        EcommerceUser user = toVerifiedUser(pendingSignup);
        EcommerceUser savedUser = ecommerceUserRepository.save(user);
        deletePendingSignup(email);
        return buildLoginResponse(savedUser);
    }

    private EcommerceUser toVerifiedUser(PendingSignup pendingSignup) {
        EcommerceUser user = new EcommerceUser();
        user.setUserId(pendingSignup.userId());
        user.setUsername(pendingSignup.username());
        user.setFullName(pendingSignup.fullName());
        user.setEmail(pendingSignup.email());
        user.setPasswordSalt(pendingSignup.passwordSalt());
        user.setPasswordHash(pendingSignup.passwordHash());
        user.setShoppingBudget(pendingSignup.shoppingBudget());
        user.setEmailVerified(true);
        user.setEmailVerificationCodeHash(null);
        user.setEmailVerificationExpiresAt(null);
        return user;
    }

    @Override
    @Transactional
    public SignupVerificationResponse resendVerificationCode(ResendVerificationRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (ecommerceUserRepository.existsByEmail(email)) {
            return new SignupVerificationResponse(email, true, "Email is already verified.");
        }

        PendingSignup pendingSignup = findPendingSignup(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Verification request was not found."));

        VerificationCode verificationCode = newVerificationCode(pendingSignup.passwordSalt());
        PendingSignup updatedPendingSignup = new PendingSignup(
                pendingSignup.userId(),
                pendingSignup.username(),
                pendingSignup.fullName(),
                pendingSignup.email(),
                pendingSignup.passwordSalt(),
                pendingSignup.passwordHash(),
                pendingSignup.shoppingBudget(),
                verificationCode.hash(),
                verificationCode.expiresAt());
        savePendingSignup(updatedPendingSignup);
        emailService.sendVerificationCode(updatedPendingSignup.email(), verificationCode.code());
        return new SignupVerificationResponse(updatedPendingSignup.email(), false, "A new verification code was sent.");
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

    private VerificationCode newVerificationCode(String salt) {
        String code = String.format("%06d", secureRandom.nextInt(1_000_000));
        return new VerificationCode(
                code,
                sha256(salt + ":email:" + code),
                Instant.now().plus(verificationExpiry));
    }

    private void savePendingSignup(PendingSignup pendingSignup) {
        localPendingSignups.put(pendingSignup.email(), pendingSignup);
        try {
            pendingSignupRedisTemplate.opsForValue()
                    .set(buildPendingSignupKey(pendingSignup.email()), pendingSignup, verificationExpiry);
        } catch (DataAccessException ex) {
            // Local development can continue without Redis; pending signup remains in memory.
        }
    }

    private java.util.Optional<PendingSignup> findPendingSignup(String email) {
        try {
            PendingSignup pendingSignup = pendingSignupRedisTemplate.opsForValue().get(buildPendingSignupKey(email));
            if (pendingSignup != null) {
                localPendingSignups.put(email, pendingSignup);
                return java.util.Optional.of(pendingSignup);
            }
        } catch (RedisConnectionFailureException ex) {
            // Fall through to local fallback.
        }

        PendingSignup pendingSignup = localPendingSignups.get(email);
        if (pendingSignup != null && Instant.now().isAfter(pendingSignup.emailVerificationExpiresAt())) {
            localPendingSignups.remove(email);
            return java.util.Optional.empty();
        }
        return java.util.Optional.ofNullable(pendingSignup);
    }

    private void deletePendingSignup(String email) {
        localPendingSignups.remove(email);
        try {
            pendingSignupRedisTemplate.delete(buildPendingSignupKey(email));
        } catch (DataAccessException ex) {
            // Redis cleanup can be retried naturally by TTL.
        }
    }

    private String buildPendingSignupKey(String email) {
        return PENDING_SIGNUP_KEY_PREFIX + email;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to verify customer password.", ex);
        }
    }

    private record VerificationCode(String code, String hash, Instant expiresAt) {
    }
}
