package com.polymer.application.port.out;

/** Sends an e-mail. In Docker it is delivered to Mailpit (development SMTP capture), never to the Internet. */
public interface MailSender {
    void send(MailMessage message);

    record MailMessage(String to, String subject, String body) {}
}
