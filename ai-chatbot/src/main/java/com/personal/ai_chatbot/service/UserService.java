package com.personal.ai_chatbot.service;

import com.personal.ai_chatbot.dto.LoginRequest;
import com.personal.ai_chatbot.dto.LoginResponse;
import com.personal.ai_chatbot.dto.LogoutResponse;
import com.personal.ai_chatbot.dto.ResendVerificationRequest;
import com.personal.ai_chatbot.dto.SignupRequest;
import com.personal.ai_chatbot.dto.SignupVerificationResponse;
import com.personal.ai_chatbot.dto.VerifyEmailRequest;

public interface UserService {

    SignupVerificationResponse signup(SignupRequest request);

    LoginResponse verifyEmail(VerifyEmailRequest request);

    SignupVerificationResponse resendVerificationCode(ResendVerificationRequest request);

    LoginResponse login(LoginRequest request);

    LogoutResponse logout(String authorizationHeader);
}
