package com.personal.ai_chatbot.service;

public interface EmailService {

    void sendVerificationCode(String toEmail, String code);
}
