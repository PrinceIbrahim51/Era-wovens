package com.aurelia.notification.infrastructure;

import com.aurelia.platform.web.Pii;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Development adapter: records that an email would be sent, with the recipient masked. Sends nothing. */
@Component
public class LoggingEmailSender implements EmailSender {
    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(String to, String subject, String body) {
        log.info("[email:dev-log-only] to={} subject=\"{}\" ({} chars)", Pii.maskEmail(to), subject, body.length());
    }
}
