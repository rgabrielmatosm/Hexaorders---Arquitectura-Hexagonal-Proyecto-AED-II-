package com.polymer.adapter.out.mail;

import com.polymer.application.port.out.MailSender;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;

/**
 * SMTP adapter. By default it points to Mailpit (development capture); with SMTP_HOST, SMTP_USERNAME and
 * the smtp_password secret it delivers through a real relay (Gmail with an app password, Brevo, SES…).
 * Messages with an HTML version are sent as multipart/alternative (plain text + HTML).
 */
public final class SmtpMailSender implements MailSender {
    private final JavaMailSender smtp;
    private final String from;

    public SmtpMailSender(JavaMailSender smtp, String from) {
        this.smtp = smtp;
        this.from = from;
    }

    @Override
    public void send(MailMessage message) {
        if (message.html() == null) {
            SimpleMailMessage mail = new SimpleMailMessage();
            mail.setFrom(from);
            mail.setTo(message.to());
            mail.setSubject(message.subject());
            mail.setText(message.body());
            smtp.send(mail); // MailException propagates: the consumer retries and finally dead-letters
            return;
        }
        MimeMessage mime = smtp.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(mime, true, StandardCharsets.UTF_8.name());
            helper.setFrom(from);
            helper.setTo(message.to());
            helper.setSubject(message.subject());
            helper.setText(message.body(), message.html());
        } catch (MessagingException invalid) {
            throw new MailPreparationException(invalid);
        }
        smtp.send(mime);
    }
}
