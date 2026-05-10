package com.personal.ai_chatbot.controller;

import com.personal.ai_chatbot.dto.LoginRequest;
import com.personal.ai_chatbot.dto.LoginResponse;
import com.personal.ai_chatbot.dto.LogoutResponse;
import com.personal.ai_chatbot.dto.ResendVerificationRequest;
import com.personal.ai_chatbot.dto.SignupRequest;
import com.personal.ai_chatbot.dto.SignupVerificationResponse;
import com.personal.ai_chatbot.dto.VerifyEmailRequest;
import com.personal.ai_chatbot.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return userService.login(request);
    }

    @PostMapping("/signup")
    public SignupVerificationResponse signup(@Valid @RequestBody SignupRequest request) {
        return userService.signup(request);
    }

    @PostMapping("/verify-email")
    public LoginResponse verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return userService.verifyEmail(request);
    }

    @PostMapping("/resend-verification")
    public SignupVerificationResponse resendVerificationCode(@Valid @RequestBody ResendVerificationRequest request) {
        return userService.resendVerificationCode(request);
    }

    @PostMapping("/logout")
    public LogoutResponse logout(@RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        return userService.logout(authorizationHeader);
    }
}
