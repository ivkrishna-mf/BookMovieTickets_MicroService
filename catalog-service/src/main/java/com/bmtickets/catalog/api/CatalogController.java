package com.bmtickets.catalog.api;

import com.bmtickets.catalog.api.CatalogDtos.CreateEventRequest;
import com.bmtickets.catalog.api.CatalogDtos.CreateShowRequest;
import com.bmtickets.catalog.api.CatalogDtos.CreateVenueRequest;
import com.bmtickets.catalog.api.CatalogDtos.EventDetails;
import com.bmtickets.catalog.api.CatalogDtos.EventPage;
import com.bmtickets.catalog.api.CatalogDtos.IdResponse;
import com.bmtickets.catalog.api.CatalogDtos.ShowResponse;
import com.bmtickets.catalog.service.CatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog")
@Validated
public class CatalogController {

    private final CatalogService service;

    public CatalogController(CatalogService service) {
        this.service = service;
    }

    // ------------- public reads (also CDN-cacheable via Cache-Control) -------------

    @GetMapping("/events")
    public ResponseEntity<EventPage> events(@RequestParam String city,
                                            @RequestParam(defaultValue = "0") @Min(0) int page,
                                            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .body(service.upcomingEvents(city, page, size));
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<EventDetails> event(@PathVariable UUID id) {
        return ResponseEntity.ok().cacheControl(CacheControl.maxAge(Duration.ofSeconds(30)).cachePublic())
                .body(service.eventDetails(id));
    }

    @GetMapping("/shows/{id}")
    public ShowResponse show(@PathVariable UUID id) {
        return service.show(id);
    }

    // ------------- admin writes (RBAC) -------------

    @PostMapping("/venues")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public IdResponse createVenue(@Valid @RequestBody CreateVenueRequest request) {
        return new IdResponse(service.createVenue(request));
    }

    @PostMapping("/events")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public IdResponse createEvent(@Valid @RequestBody CreateEventRequest request) {
        return new IdResponse(service.createEvent(request));
    }

    @PostMapping("/shows")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public IdResponse createShow(@Valid @RequestBody CreateShowRequest request) {
        return new IdResponse(service.createShow(request));
    }

    @PostMapping("/shows/{id}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void cancelShow(@PathVariable UUID id) {
        service.cancelShow(id);
    }
}
