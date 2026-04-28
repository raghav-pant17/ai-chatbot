package com.personal.ai_chatbot.service;

import com.personal.ai_chatbot.dto.LoginRequest;
import com.personal.ai_chatbot.dto.LoginResponse;
import com.personal.ai_chatbot.dto.LogoutResponse;

public interface UserService {

    LoginResponse login(LoginRequest request);

    LogoutResponse logout(String authorizationHeader);
}
