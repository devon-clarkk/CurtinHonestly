package com.curtinhonestly.backend.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Sends transactional email over SMTP. Provider-agnostic: point {@code spring.mail.*}
 * at Resend / Brevo / SES SMTP. When SMTP is not configured (no {@code spring.mail.host}
 * or no {@link JavaMailSender} bean), it logs the message instead of throwing, so
 * flows like registration never break locally or on an unconfigured environment.
 *
 * <p>Neither send method ever throws. Callers run inside transactions that must not
 * roll back because a mail relay hiccuped; the user can always re-request a link.
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

    /** Plain-text only. Use {@link #send(String, String, String, String)} when the mail carries a link. */
    public void send(String to, String subject, String body) {
        JavaMailSender sender = configuredSender(to, subject, body);
        if (sender == null) {
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
            log.error("Failed to send email to={}, subject='{}': {}", to, subject, e.getMessage());
        }
    }

    /**
     * Send a {@code multipart/alternative} message: an HTML part (so a link can be a
     * real {@code <a href>} rather than a bare URL a mail client has to guess the
     * boundaries of) plus a plain-text part for clients that prefer or require it.
     * The text part is also what gets logged when SMTP is not configured.
     */
    public void send(String to, String subject, String textBody, String htmlBody) {
        JavaMailSender sender = configuredSender(to, subject, textBody);
        if (sender == null) {
            return;
        }

        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(textBody, htmlBody);
            sender.send(message);
            log.info("Sent email to={}, subject='{}'", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to={}, subject='{}': {}", to, subject, e.getMessage());
        }
    }

    /**
     * The sender to use, or {@code null} after logging the would-be message when SMTP
     * is not configured. Logging the body is what makes local development workable:
     * the emailed link is only ever visible in the log.
     */
    private JavaMailSender configuredSender(String to, String subject, String textBody) {
        JavaMailSender sender = configured ? mailSenderProvider.getIfAvailable() : null;
        if (sender == null) {
            log.warn("Email not configured (spring.mail.host unset), skipping real send. "
                    + "Would send to={}, subject='{}'.\n{}", to, subject, textBody);
        }
        return sender;
    }
}
