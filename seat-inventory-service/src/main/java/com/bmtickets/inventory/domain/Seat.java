package com.bmtickets.inventory.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.UUID;

/**
 * One physical seat for one show. The (show_id, seat_number) unique constraint plus the state machine
 * AVAILABLE -> HELD -> BOOKED (or HELD -> AVAILABLE) is the source of truth against double booking.
 */
@Entity
@Table(name = "seat", uniqueConstraints = @UniqueConstraint(name = "uk_seat_show_number", columnNames = {"show_id", "seat_number"}))
public class Seat {

    @Id
    private UUID id;

    @Column(name = "show_id", nullable = false)
    private UUID showId;

    @Column(name = "seat_number", nullable = false)
    private String seatNumber;

    @Column(name = "row_label", nullable = false)
    private String rowLabel;

    @Column(name = "seat_index", nullable = false)
    private int seatIndex;

    @Column(nullable = false)
    private String category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SeatStatus status;

    /** Booking id that holds/owns the seat. Kept after BOOKED so confirm retries are idempotent. */
    @Column(name = "hold_ref")
    private UUID holdRef;

    @Column(name = "hold_expires_at")
    private Instant holdExpiresAt;

    @Version
    private long version;

    protected Seat() {
    }

    public Seat(UUID showId, String rowLabel, int seatIndex, String category) {
        this.id = UUID.randomUUID();
        this.showId = showId;
        this.rowLabel = rowLabel;
        this.seatIndex = seatIndex;
        this.seatNumber = rowLabel + "-" + seatIndex;
        this.category = category;
        this.status = SeatStatus.AVAILABLE;
    }

    /** Holdable if free, if a previous hold expired, or if already held by the same booking (idempotent retry). */
    public boolean isHoldableBy(UUID ref, Instant now) {
        return switch (status) {
            case AVAILABLE -> true;
            case HELD -> ref.equals(holdRef) || (holdExpiresAt != null && holdExpiresAt.isBefore(now));
            case BOOKED -> false;
        };
    }

    public void hold(UUID ref, Instant expiresAt) {
        this.status = SeatStatus.HELD;
        this.holdRef = ref;
        this.holdExpiresAt = expiresAt;
    }

    public void book() {
        this.status = SeatStatus.BOOKED;
        this.holdExpiresAt = null;
    }

    public void release() {
        this.status = SeatStatus.AVAILABLE;
        this.holdRef = null;
        this.holdExpiresAt = null;
    }

    /** Status as seen by customers: an expired hold is effectively available. */
    public SeatStatus effectiveStatus(Instant now) {
        if (status == SeatStatus.HELD && holdExpiresAt != null && holdExpiresAt.isBefore(now)) {
            return SeatStatus.AVAILABLE;
        }
        return status;
    }

    public UUID getId() { return id; }
    public UUID getShowId() { return showId; }
    public String getSeatNumber() { return seatNumber; }
    public String getRowLabel() { return rowLabel; }
    public int getSeatIndex() { return seatIndex; }
    public String getCategory() { return category; }
    public SeatStatus getStatus() { return status; }
    public UUID getHoldRef() { return holdRef; }
    public Instant getHoldExpiresAt() { return holdExpiresAt; }
}
