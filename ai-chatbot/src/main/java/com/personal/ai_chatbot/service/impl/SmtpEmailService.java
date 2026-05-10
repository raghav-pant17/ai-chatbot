package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SmtpEmailService implements EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String mailPassword;

    public SmtpEmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String fromEmail,
            @Value("${spring.mail.password:}") String mailPassword) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.mailPassword = mailPassword;
    }

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        if (!StringUtils.hasText(fromEmail) || !StringUtils.hasText(mailPassword)) {
            LOGGER.warn("Email credentials are not configured. Verification code for {} is {}", toEmail, code);
            return;
        }

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromEmail);
        message.setTo(toEmail);
        message.setSubject("Verify your AI Chatbot account");
        message.setText("""
                Your verification code is: %s

                This code expires in 10 minutes.
                """.formatted(code));

        try {
            mailSender.send(message);
        } catch (MailException ex) {
            LOGGER.warn("Could not send verification email to {}. Verification code is {}", toEmail, code, ex);
        }
    }
}
