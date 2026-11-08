package com.polymer.adapter.out.mail;

import com.polymer.application.port.out.MailSender;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** SMTP adapter. In Docker it points to Mailpit (development capture); nothing reaches the Internet. */
public final class SmtpMailSender implements MailSender {
    private final JavaMailSender smtp;
    private final String from;

    public SmtpMailSender(JavaMailSender smtp, String from) {
        this.smtp = smtp;
        this.from = from;
    }

    @Override
    public void send(MailMessage message) {
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setFrom(from);
        mail.setTo(message.to());
        mail.setSubject(message.subject());
        mail.setText(message.body());
        smtp.send(mail); // MailException propagates: the consumer retries and finally dead-letters
    }
}
