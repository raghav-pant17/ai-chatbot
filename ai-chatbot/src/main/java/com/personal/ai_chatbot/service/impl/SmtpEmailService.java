package com.personal.ai_chatbot.service.impl;

import com.personal.ai_chatbot.service.EmailService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
public class SmtpEmailService implements EmailService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final String fromEmail;
    private final String mailPassword;
    private final String mailHost;
    private final int mailPort;
    private final int connectionTimeoutMs;
    private final int readTimeoutMs;
    private final int writeTimeoutMs;

    public SmtpEmailService(
            JavaMailSender mailSender,
            @Value("${spring.mail.username:}") String fromEmail,
            @Value("${spring.mail.password:}") String mailPassword,
            @Value("${spring.mail.host:smtp.gmail.com}") String mailHost,
            @Value("${spring.mail.port:587}") int mailPort,
            @Value("${spring.mail.properties.mail.smtp.connectiontimeout:5000}") int connectionTimeoutMs,
            @Value("${spring.mail.properties.mail.smtp.timeout:10000}") int readTimeoutMs,
            @Value("${spring.mail.properties.mail.smtp.writetimeout:10000}") int writeTimeoutMs) {
        this.mailSender = mailSender;
        this.fromEmail = fromEmail;
        this.mailPassword = mailPassword;
        this.mailHost = mailHost;
        this.mailPort = mailPort;
        this.connectionTimeoutMs = connectionTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
        this.writeTimeoutMs = writeTimeoutMs;
        applyMailTimeouts(mailSender);
    }

    @PostConstruct
    public void logMailSetup() {
        if (!StringUtils.hasText(fromEmail) || !StringUtils.hasText(mailPassword)) {
            LOGGER.warn("SMTP mail setup incomplete host={} port={} usernameConfigured={} passwordConfigured={}",
                    mailHost,
                    mailPort,
                    StringUtils.hasText(fromEmail),
                    StringUtils.hasText(mailPassword));
            return;
        }
        LOGGER.info(
                "SMTP mail setup ready host={} port={} username={} connectionTimeoutMs={} readTimeoutMs={} writeTimeoutMs={}",
                mailHost,
                mailPort,
                fromEmail,
                connectionTimeoutMs,
                readTimeoutMs,
                writeTimeoutMs);
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
            LOGGER.warn(
                    "SMTP verification email initiation failed to={} elapsedMs={} rootCause={}",
                    toEmail,
                    elapsedMillis(startedAt),
                    rootCauseMessage(ex));
            LOGGER.debug("SMTP verification email initiation stack trace", ex);
        } catch (MailException ex) {
            LOGGER.warn(
                    "SMTP verification email send failed to={} host={} port={} elapsedMs={} rootCause={}. Verification code is {}",
                    toEmail,
                    mailHost,
                    mailPort,
                    elapsedMillis(startedAt),
                    rootCauseMessage(ex),
                    code);
            LOGGER.debug("SMTP verification email send stack trace", ex);
        }
    }

    private void applyMailTimeouts(JavaMailSender mailSender) {
        if (!(mailSender instanceof JavaMailSenderImpl javaMailSender)) {
            return;
        }

        javaMailSender.getJavaMailProperties()
                .put("mail.smtp.connectiontimeout", String.valueOf(connectionTimeoutMs));
        javaMailSender.getJavaMailProperties()
                .put("mail.smtp.timeout", String.valueOf(readTimeoutMs));
        javaMailSender.getJavaMailProperties()
                .put("mail.smtp.writetimeout", String.valueOf(writeTimeoutMs));
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

    private String rootCauseMessage(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null) {
            rootCause = rootCause.getCause();
        }
        String message = rootCause.getMessage();
        return rootCause.getClass().getSimpleName() + (StringUtils.hasText(message) ? ": " + message : "");
    }
}
