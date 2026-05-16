package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.service.EmailService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

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

    @PostConstruct
    public void logMailSetup() {
        if (!StringUtils.hasText(fromEmail) || !StringUtils.hasText(mailPassword)) {
            LOGGER.warn("SMTP mail setup incomplete usernameConfigured={} passwordConfigured={}",
                    StringUtils.hasText(fromEmail),
                    StringUtils.hasText(mailPassword));
            return;
        }
        LOGGER.info("SMTP mail setup ready username={}", fromEmail);
    }

    @Override
    public void sendVerificationCode(String toEmail, String code) {
        if (!StringUtils.hasText(fromEmail) || !StringUtils.hasText(mailPassword)) {
            LOGGER.warn("Email credentials are not configured. Verification code for {} is {}", toEmail, code);
            return;
        }

        long startedAt = System.nanoTime();
        LOGGER.info("SMTP verification email send started to={}", toEmail);

        try {
            SimpleMailMessage message = buildVerificationMessage(toEmail, code);
            mailSender.send(message);
            LOGGER.info("SMTP verification email send completed to={} elapsedMs={}", toEmail, elapsedMillis(startedAt));
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("SMTP verification email initiation failed to={} elapsedMs={} error={}", toEmail, elapsedMillis(startedAt), ex.getMessage(), ex);
        } catch (MailException ex) {
            LOGGER.warn("SMTP verification email send failed to={} elapsedMs={}. Verification code is {}", toEmail, elapsedMillis(startedAt), code, ex);
        }
    }

    private SimpleMailMessage buildVerificationMessage(String toEmail, String code) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(toEmail);
            message.setSubject("Verify your AI Chatbot account");
            message.setText("""
                    Your verification code is: %s

                    This code expires in 10 minutes.
                    """.formatted(code));
            return message;
        } catch (IllegalArgumentException ex) {
            LOGGER.warn("SMTP verification email message initialization failed to={} error={}", toEmail, ex.getMessage());
            throw ex;
        }
    }

    private long elapsedMillis(long startedAt) {
        return Duration.ofNanos(System.nanoTime() - startedAt).toMillis();
    }
}
