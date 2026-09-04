package com.curtinhonestly.backend.service;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * The emailed verification link is the only copy of a single-use credential, so
 * the message that carries it has to arrive with the URL intact: as a real anchor
 * in an HTML part, with a plain-text alternative, and never as a reason for the
 * calling transaction to fail.
 */
@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock ObjectProvider<JavaMailSender> senderProvider;
    @Mock JavaMailSender sender;

    private EmailService configuredService() {
        when(senderProvider.getIfAvailable()).thenReturn(sender);
        return new EmailService(senderProvider, "no-reply@curtinhonestly.com", "smtp.example.test");
    }

    @Test
    void htmlSendBuildsAMultipartAlternativeWithTextAndHtmlParts() throws Exception {
        EmailService service = configuredService();
        // A real MimeMessage (from an unconnected JavaMailSenderImpl) so the helper has
        // something to populate; only the transport is mocked.
        when(sender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        String link = "https://curtinhonestly.com/verify-student/confirm?token=" + "ab".repeat(32);

        service.send("bob@student.curtin.edu.au", "Verify your Curtin student email",
                "Open this link:\n" + link + "\n",
                "<p><a href=\"" + link + "\">Confirm my student email</a></p>");

        ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
        verify(sender).send(captor.capture());
        MimeMessage sent = captor.getValue();
        sent.saveChanges();

        assertThat(sent.getSubject()).isEqualTo("Verify your Curtin student email");
        assertThat(sent.getAllRecipients()).extracting(Object::toString).containsExactly("bob@student.curtin.edu.au");
        assertThat(sent.getFrom()).extracting(Object::toString).containsExactly("no-reply@curtinhonestly.com");

        Multipart alternative = innermostMultipart(sent.getContent());
        assertThat(alternative.getContentType()).startsWith("multipart/alternative");
        assertThat(alternative.getCount()).isEqualTo(2);
        BodyPart text = alternative.getBodyPart(0);
        BodyPart html = alternative.getBodyPart(1);
        assertThat(text.getContentType()).startsWith("text/plain");
        assertThat(html.getContentType()).startsWith("text/html");
        assertThat(text.getContent().toString()).contains(link);
        assertThat(html.getContent().toString()).contains("href=\"" + link + "\"");

        // The full link must survive serialisation on one line: a soft wrap inside the
        // token is exactly the kind of truncation a copy-and-paste fallback then inherits.
        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        sent.writeTo(raw);
        assertThat(raw.toString(StandardCharsets.UTF_8)).contains(link);
    }

    @Test
    void htmlSendNeverThrowsWhenTheRelayFails() {
        EmailService service = configuredService();
        when(sender.createMimeMessage()).thenReturn(new JavaMailSenderImpl().createMimeMessage());
        doThrow(new MailSendException("relay down")).when(sender).send(any(MimeMessage.class));

        assertThatCode(() -> service.send("bob@student.curtin.edu.au", "subject", "text", "<p>html</p>"))
                .doesNotThrowAnyException();
    }

    @Test
    void htmlSendLogsInsteadOfSendingWhenSmtpIsNotConfigured() {
        EmailService service = new EmailService(senderProvider, "no-reply@curtinhonestly.com", "");

        assertThatCode(() -> service.send("bob@student.curtin.edu.au", "subject", "text", "<p>html</p>"))
                .doesNotThrowAnyException();

        verifyNoInteractions(senderProvider, sender);
    }

    // MimeMessageHelper in multipart mode nests: mixed -> related -> alternative.
    private static Multipart innermostMultipart(Object content) throws Exception {
        Multipart current = (Multipart) content;
        while (true) {
            if (current.getContentType().startsWith("multipart/alternative")) {
                return current;
            }
            Object inner = current.getBodyPart(0).getContent();
            if (!(inner instanceof Multipart next)) {
                return current;
            }
            current = next;
        }
    }
}
