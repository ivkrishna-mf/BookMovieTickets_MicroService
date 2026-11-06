package com.bmtickets.booking.api;

import com.bmtickets.booking.api.BookingDtos.BookingResponse;
import com.bmtickets.booking.api.BookingDtos.CreateBookingRequest;
import com.bmtickets.booking.domain.Booking;
import com.bmtickets.booking.domain.BookingRepository;
import com.bmtickets.booking.idempotency.IdempotencyService;
import com.bmtickets.booking.saga.BookingSagaOrchestrator;
import com.bmtickets.common.error.BusinessException;
import com.bmtickets.common.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/bookings")
@Validated
public class BookingController {

    private final BookingSagaOrchestrator saga;
    private final IdempotencyService idempotency;
    private final BookingRepository bookings;

    public BookingController(BookingSagaOrchestrator saga, IdempotencyService idempotency, BookingRepository bookings) {
        this.saga = saga;
        this.idempotency = idempotency;
        this.bookings = bookings;
    }

    /**
     * Create a booking. The client MUST send a unique {@code Idempotency-Key} (UUID) per booking intent and reuse it
     * on retries (network error, timeout, double click) - the booking is then executed at most once.
     */
    @PostMapping
    public ResponseEntity<BookingResponse> create(
            @AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9-]{16,64}") String idempotencyKey,
            @Valid @RequestBody CreateBookingRequest request) {
        CurrentUser user = CurrentUser.from(jwt, false);
        var outcome = idempotency.execute("booking:" + user.id(), idempotencyKey, fingerprint(request),
                BookingResponse.class, () -> saga.createBooking(user, request, idempotencyKey));
        return ResponseEntity.status(HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(outcome.replayed()))
                .body(outcome.value());
    }

    @GetMapping("/{id}")
    public BookingResponse get(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt, Authentication auth) {
        Booking b = bookings.findById(id).orElseThrow(() -> BusinessException.notFound("Booking", id));
        if (!b.getUserId().equals(jwt.getSubject()) && !isAdmin(auth)) {
            throw BusinessException.notFound("Booking", id);          // ownership check (no IDOR)
        }
        return BookingResponse.from(b);
    }

    @GetMapping
    public Page<BookingResponse> mine(@AuthenticationPrincipal Jwt jwt,
                                      @RequestParam(defaultValue = "0") @Min(0) int page,
                                      @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return bookings.findByUserIdOrderByCreatedAtDesc(jwt.getSubject(), PageRequest.of(page, size))
                .map(BookingResponse::from);
    }

    @PostMapping("/{id}/cancel")
    public BookingResponse cancel(@PathVariable UUID id, @AuthenticationPrincipal Jwt jwt) {
        return saga.cancelPending(id, jwt.getSubject());
    }

    private static boolean isAdmin(Authentication auth) {
        return auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private static String fingerprint(CreateBookingRequest r) {
        String canonical = r.showId() + "|" + String.join(",", r.seats().stream().distinct().sorted().toList());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
