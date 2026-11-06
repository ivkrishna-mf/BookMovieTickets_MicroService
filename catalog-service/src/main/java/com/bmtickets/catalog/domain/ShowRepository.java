package com.bmtickets.catalog.domain;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShowRepository extends JpaRepository<Show, UUID> {

    @EntityGraph(attributePaths = {"event", "venue"})
    @Query("SELECT s FROM Show s WHERE s.id = :id")
    Optional<Show> findDetailedById(@Param("id") UUID id);

    @EntityGraph(attributePaths = {"venue"})
    @Query("SELECT s FROM Show s WHERE s.event.id = :eventId AND s.startTime > :now ORDER BY s.startTime")
    List<Show> findUpcomingByEvent(@Param("eventId") UUID eventId, @Param("now") Instant now);
}
