package com.bmtickets.booking.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, UUID> {

    Optional<Booking> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);

    Page<Booking> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    @Query("""
            SELECT b.id FROM Booking b
            WHERE (b.status = com.bmtickets.booking.domain.BookingStatus.PENDING_PAYMENT AND b.expiresAt < :now)
               OR (b.status = com.bmtickets.booking.domain.BookingStatus.PENDING_SEATS AND b.createdAt < :stuckBefore)
            ORDER BY b.createdAt
            """)
    List<UUID> findExpiredIds(@Param("now") Instant now, @Param("stuckBefore") Instant stuckBefore, Pageable pageable);
}
