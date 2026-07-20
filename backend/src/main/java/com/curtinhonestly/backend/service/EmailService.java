package com.curtinhonestly.backend.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

/**
 * Sends transactional email over SMTP. Provider-agnostic — point {@code spring.mail.*}
 * at Resend / Brevo / SES SMTP. When SMTP is not configured (no {@code spring.mail.host}
 * or no {@link JavaMailSender} bean), it logs the message instead of throwing, so
 * flows like registration never break locally or on an unconfigured environment.
 */
@Service
@Slf4j
public class EmailService {

    private final ObjectProvider<JavaMailSender> mailSenderProvider;
    private final String from;
    private final boolean configured;

    public EmailService(ObjectProvider<JavaMailSender> mailSenderProvider,
                        @Value("${app.mail.from:no-reply@curtinhonestly.com}") String from,
                        @Value("${spring.mail.host:}") String mailHost) {
        this.mailSenderProvider = mailSenderProvider;
        this.from = from;
        this.configured = mailHost != null && !mailHost.isBlank();
    }

    public void send(String to, String subject, String body) {
        JavaMailSender sender = configured ? mailSenderProvider.getIfAvailable() : null;
        if (sender == null) {
            log.warn("Email not configured (spring.mail.host unset) — skipping real send. "
                    + "Would send to={}, subject='{}'.\n{}", to, subject, body);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            sender.send(message);
            log.info("Sent email to={}, subject='{}'", to, subject);
        } catch (Exception e) {
            // Never let a mail failure roll back the caller's transaction (e.g. registration).
            // The user can re-request the link.
            log.error("Failed to send email to={}, subject='{}': {}", to, subject, e.getMessage());
        }
    }
}
