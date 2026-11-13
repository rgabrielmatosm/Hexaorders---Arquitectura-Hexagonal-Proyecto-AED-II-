package com.polymer.application.port.out;

/**
 * Sends an e-mail. By default it is delivered to Mailpit (development SMTP capture); with SMTP_HOST and
 * the smtp_password secret it is delivered through a real SMTP relay (e.g. Gmail) to the customer's inbox.
 */
public interface MailSender {
    void send(MailMessage message);

    /** body: texto plano (siempre); html: versión HTML opcional (boleta, código de verificación). */
    record MailMessage(String to, String subject, String body, String html) {
        public MailMessage(String to, String subject, String body) { this(to, subject, body, null); }
    }
}
