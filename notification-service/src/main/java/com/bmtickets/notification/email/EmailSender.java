package com.bmtickets.notification.email;

/** Port for outbound e-mail; adapters: Amazon SES (prod) and logging (local). */
public interface EmailSender {
    /** @return provider message id */
    String send(String to, String subject, String html, String text);
}
