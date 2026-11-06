package com.bmtickets.catalog.service;

import com.bmtickets.catalog.api.CatalogDtos.CreateEventRequest;
import com.bmtickets.catalog.api.CatalogDtos.CreateShowRequest;
import com.bmtickets.catalog.api.CatalogDtos.CreateVenueRequest;
import com.bmtickets.catalog.api.CatalogDtos.EventDetails;
import com.bmtickets.catalog.api.CatalogDtos.EventPage;
import com.bmtickets.catalog.api.CatalogDtos.EventSummary;
import com.bmtickets.catalog.api.CatalogDtos.ShowResponse;
import com.bmtickets.catalog.api.CatalogDtos.ShowSummary;
import com.bmtickets.catalog.domain.Event;
import com.bmtickets.catalog.domain.EventRepository;
import com.bmtickets.catalog.domain.Show;
import com.bmtickets.catalog.domain.ShowRepository;
import com.bmtickets.catalog.domain.Venue;
import com.bmtickets.catalog.domain.VenueRepository;
import com.bmtickets.common.error.BusinessException;
import com.bmtickets.common.events.EventTypes;
import com.bmtickets.common.events.ShowScheduledEvent;
import com.bmtickets.common.events.Topics;
import com.bmtickets.common.messaging.OutboxService;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Cache-aside with Redis: reads are served from ElastiCache; writes evict. Catalog data changes rarely and is read
 * by every visitor, so this removes most database load (CQRS-lite: separate optimized read path).
 */
@Service
public class CatalogService {

    private final VenueRepository venues;
    private final EventRepository events;
    private final ShowRepository shows;
    private final OutboxService outbox;

    public CatalogService(VenueRepository venues, EventRepository events, ShowRepository shows, OutboxService outbox) {
        this.venues = venues;
        this.events = events;
        this.shows = shows;
        this.outbox = outbox;
    }

    @Transactional
    public UUID createVenue(CreateVenueRequest r) {
        return venues.save(new Venue(r.name(), r.city(), r.address(), r.capacity())).getId();
    }

    @Transactional
    public UUID createEvent(CreateEventRequest r) {
        return events.save(new Event(r.title(), r.description(), r.category(), r.language(), r.durationMinutes())).getId();
    }

    /** Persists the show and, atomically via the outbox, announces it so seat-inventory builds the seat map. */
    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.EVENTS_BY_CITY, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.EVENT, key = "#r.eventId()")})
    public UUID createShow(CreateShowRequest r) {
        Event event = events.findById(r.eventId()).orElseThrow(() -> BusinessException.notFound("Event", r.eventId()));
        Venue venue = venues.findById(r.venueId()).orElseThrow(() -> BusinessException.notFound("Venue", r.venueId()));
        boolean pricesCoverSections = r.sections().stream().allMatch(s -> r.priceByCategory().containsKey(s.category()));
        if (!pricesCoverSections) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "PRICE_MISSING", "Every seat section category needs a price");
        }
        Show show = shows.save(new Show(event, venue, r.startTime(), r.currency(), r.maxSeatsPerBooking(), r.priceByCategory()));
        outbox.record("Show", show.getId(), EventTypes.SHOW_SCHEDULED, Topics.SHOW_EVENTS,
                new ShowScheduledEvent(show.getId(), venue.getId(), show.getStartTime(), r.sections()));
        return show.getId();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.EVENTS_BY_CITY, key = "#city.toLowerCase() + ':' + #page + ':' + #size", sync = true)
    public EventPage upcomingEvents(String city, int page, int size) {
        Page<Event> p = events.findUpcomingInCity(city, Instant.now(), PageRequest.of(page, size));
        return new EventPage(
                p.map(e -> new EventSummary(e.getId(), e.getTitle(), e.getCategory(), e.getLanguage(), e.getDurationMinutes())).getContent(),
                p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.EVENT, key = "#id", sync = true)
    public EventDetails eventDetails(UUID id) {
        Event e = events.findById(id).orElseThrow(() -> BusinessException.notFound("Event", id));
        var upcoming = shows.findUpcomingByEvent(id, Instant.now()).stream()
                .map(s -> new ShowSummary(s.getId(), s.getVenue().getId(), s.getVenue().getName(), s.getVenue().getCity(),
                        s.getStartTime(), s.getStatus().name()))
                .toList();
        return new EventDetails(e.getId(), e.getTitle(), e.getDescription(), e.getCategory(), e.getLanguage(),
                e.getDurationMinutes(), upcoming);
    }

    /** Hot path: called by every booking attempt (via gRPC) and every show page view. */
    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheNames.SHOW, key = "#id", sync = true)
    public ShowResponse show(UUID id) {
        Show s = shows.findDetailedById(id).orElseThrow(() -> BusinessException.notFound("Show", id));
        return new ShowResponse(s.getId(), s.getEvent().getId(), s.getEvent().getTitle(), s.getVenue().getId(),
                s.getVenue().getName(), s.getVenue().getCity(), s.getStartTime(), s.getStatus().name(), s.getCurrency(),
                s.getMaxSeatsPerBooking(), java.util.Map.copyOf(s.getPriceByCategory()));
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(cacheNames = CacheNames.SHOW, key = "#id"),
            @CacheEvict(cacheNames = CacheNames.EVENTS_BY_CITY, allEntries = true),
            @CacheEvict(cacheNames = CacheNames.EVENT, allEntries = true)})
    public void cancelShow(UUID id) {
        Show s = shows.findById(id).orElseThrow(() -> BusinessException.notFound("Show", id));
        s.cancel();
    }
}
