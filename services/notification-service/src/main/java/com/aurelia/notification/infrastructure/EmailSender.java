package com.aurelia.notification.infrastructure;

/** Transactional email port. A provider adapter (SES, SendGrid, ...) will replace the logging adapter. */
public interface EmailSender {
    void send(String to, String subject, String body);
}
