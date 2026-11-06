package com.bmtickets.catalog.domain;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {

    @Query(value = """
            SELECT DISTINCT e FROM Show s JOIN s.event e JOIN s.venue v
            WHERE lower(v.city) = lower(:city) AND s.startTime > :now AND s.status = com.bmtickets.catalog.domain.ShowStatus.OPEN
            """,
            countQuery = """
            SELECT COUNT(DISTINCT e) FROM Show s JOIN s.event e JOIN s.venue v
            WHERE lower(v.city) = lower(:city) AND s.startTime > :now AND s.status = com.bmtickets.catalog.domain.ShowStatus.OPEN
            """)
    Page<Event> findUpcomingInCity(@Param("city") String city, @Param("now") Instant now, Pageable pageable);
}
