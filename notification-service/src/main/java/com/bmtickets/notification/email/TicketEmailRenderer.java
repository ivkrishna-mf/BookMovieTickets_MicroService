package com.bmtickets.notification.email;

import com.bmtickets.common.events.BookingCancelledEvent;
import com.bmtickets.common.events.BookingConfirmedEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class TicketEmailRenderer {

    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy 'at' HH:mm z")
            .withZone(ZoneId.of("Asia/Kolkata"));

    public record RenderedEmail(String subject, String html, String text) {
    }

    public RenderedEmail confirmed(BookingConfirmedEvent e) {
        String seats = String.join(", ", e.seats());
        String subject = "Your tickets: " + e.eventTitle();
        String text = """
                Booking confirmed!

                %s
                %s - %s
                Seats: %s
                Amount paid: %s %s
                Booking id: %s

                Ticket code (show at entry): %s
                """.formatted(e.eventTitle(), e.venueName(), WHEN.format(e.showTime()), seats,
                e.totalAmount(), e.currency(), e.bookingId(), e.ticketCode());
        String html = """
                <html><body style="font-family:Arial,sans-serif">
                <h2>Booking confirmed</h2>
                <p><strong>%s</strong><br/>%s &middot; %s</p>
                <p>Seats: <strong>%s</strong><br/>Amount paid: %s %s<br/>Booking id: %s</p>
                <p>Show this code at the entrance:</p>
                <p style="font-size:18px;font-family:monospace;padding:12px;border:1px dashed #999">%s</p>
                </body></html>
                """.formatted(esc(e.eventTitle()), esc(e.venueName()), esc(WHEN.format(e.showTime())), esc(seats),
                e.totalAmount(), esc(e.currency()), e.bookingId(), esc(e.ticketCode()));
        return new RenderedEmail(subject, html, text);
    }

    public RenderedEmail cancelled(BookingCancelledEvent e) {
        String subject = "Your booking was not completed";
        String text = "Booking " + e.bookingId() + " was not completed (" + e.reason() + "). "
                + "Any amount charged will be refunded automatically.";
        String html = "<html><body style=\"font-family:Arial,sans-serif\"><p>" + esc(text) + "</p></body></html>";
        return new RenderedEmail(subject, html, text);
    }

    private static String esc(String s) {
        return s == null ? "" : HtmlUtils.htmlEscape(s);
    }
}
