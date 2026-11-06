package com.bmtickets.notification.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "app.notification.channel", havingValue = "log", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public String send(String to, String subject, String html, String text) {
        log.info("EMAIL to={} subject='{}'\n{}", to, subject, text);
        return "log-" + UUID.randomUUID();
    }
}
