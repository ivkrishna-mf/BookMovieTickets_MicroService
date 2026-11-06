package com.bmtickets.inventory.api;

import java.util.List;
import java.util.UUID;

public record SeatMapResponse(UUID showId, long available, List<SeatView> seats) {

    public record SeatView(String seatNumber, String row, int index, String category, String status) {
    }
}
