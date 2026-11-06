package com.bmtickets.inventory.domain;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SeatRepository extends JpaRepository<Seat, UUID> {

    /**
     * SELECT ... ORDER BY seat_number FOR UPDATE: row locks taken in a deterministic order, so two bookings that
     * overlap on seats can never deadlock; the loser waits, then sees the seat as HELD and fails cleanly.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.showId = :showId AND s.seatNumber IN :seatNumbers ORDER BY s.seatNumber")
    List<Seat> lockSeats(@Param("showId") UUID showId, @Param("seatNumbers") Collection<String> seatNumbers);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Seat s WHERE s.holdRef = :holdRef ORDER BY s.seatNumber")
    List<Seat> lockByHoldRef(@Param("holdRef") UUID holdRef);

    List<Seat> findByShowIdOrderByRowLabelAscSeatIndexAsc(UUID showId);

    boolean existsByShowId(UUID showId);

    @Query("SELECT s FROM Seat s WHERE s.status = com.bmtickets.inventory.domain.SeatStatus.HELD AND s.holdExpiresAt < :now")
    List<Seat> findExpiredHolds(@Param("now") Instant now, org.springframework.data.domain.Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Seat s SET s.status = com.bmtickets.inventory.domain.SeatStatus.AVAILABLE,
                              s.holdRef = null, s.holdExpiresAt = null, s.version = s.version + 1
            WHERE s.id IN :ids AND s.status = com.bmtickets.inventory.domain.SeatStatus.HELD AND s.holdExpiresAt < :now
            """)
    int releaseExpired(@Param("ids") Collection<UUID> ids, @Param("now") Instant now);
}
