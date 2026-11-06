package com.bmtickets.booking.ticket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Tamper-proof ticket code (rendered as a QR code): bookingId + truncated HMAC-SHA256 over the booking facts.
 * Venue scanners verify it offline with the shared key (stored in AWS Secrets Manager), no lookup needed.
 */
@Component
public class TicketCodeGenerator {

    private final byte[] key;

    public TicketCodeGenerator(@Value("${app.ticket.signing-key}") String signingKey) {
        if (signingKey == null || signingKey.length() < 32) {
            throw new IllegalStateException("app.ticket.signing-key must be at least 32 characters");
        }
        this.key = signingKey.getBytes(StandardCharsets.UTF_8);
    }

    public String generate(UUID bookingId, UUID showId, List<String> seats) {
        String payload = bookingId + "|" + showId + "|" + String.join(",", seats);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] sig = Arrays.copyOf(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)), 16);
            return bookingId + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
