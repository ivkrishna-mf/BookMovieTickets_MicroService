package com.bmtickets.inventory.api;

import com.bmtickets.inventory.domain.Seat;
import com.bmtickets.inventory.domain.SeatRepository;
import com.bmtickets.inventory.domain.SeatStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shows")
public class SeatMapController {

    private final SeatRepository seats;
    private final SeatMapCache cache;

    public SeatMapController(SeatRepository seats, SeatMapCache cache) {
        this.seats = seats;
        this.cache = cache;
    }

    @GetMapping("/{showId}/seats")
    @Transactional(readOnly = true)
    public SeatMapResponse seatMap(@PathVariable UUID showId) {
        return cache.get(showId).orElseGet(() -> {
            Instant now = Instant.now();
            List<SeatMapResponse.SeatView> views = seats.findByShowIdOrderByRowLabelAscSeatIndexAsc(showId).stream()
                    .map(s -> view(s, now)).toList();
            long available = views.stream().filter(v -> SeatStatus.AVAILABLE.name().equals(v.status())).count();
            SeatMapResponse response = new SeatMapResponse(showId, available, views);
            cache.put(showId, response);
            return response;
        });
    }

    private static SeatMapResponse.SeatView view(Seat s, Instant now) {
        // HELD and BOOKED both render as unavailable; we never leak who holds a seat.
        return new SeatMapResponse.SeatView(s.getSeatNumber(), s.getRowLabel(), s.getSeatIndex(), s.getCategory(),
                s.effectiveStatus(now).name());
    }
}
