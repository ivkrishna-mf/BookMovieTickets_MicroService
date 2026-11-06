package com.bmtickets.booking.client;

import com.bmtickets.grpc.inventory.v1.HoldRefRequest;
import com.bmtickets.grpc.inventory.v1.HoldSeatsRequest;
import com.bmtickets.grpc.inventory.v1.HoldSeatsResponse;
import com.bmtickets.grpc.inventory.v1.SeatInventoryServiceGrpc;
import com.bmtickets.grpc.inventory.v1.SeatOperationResponse;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * gRPC commands to seat-inventory. Every command carries holdRef = bookingId, so retries after a timeout
 * (where the first attempt may actually have succeeded) are safe.
 */
@Component
public class InventoryClient {

    @GrpcClient("seat-inventory")
    private SeatInventoryServiceGrpc.SeatInventoryServiceBlockingStub stub;

    @Retry(name = "inventory")
    @CircuitBreaker(name = "inventory")
    public HoldSeatsResponse hold(UUID showId, List<String> seats, UUID holdRef, Duration ttl) {
        return stub.withDeadlineAfter(3, TimeUnit.SECONDS).holdSeats(HoldSeatsRequest.newBuilder()
                .setShowId(showId.toString())
                .addAllSeatNumbers(seats)
                .setHoldRef(holdRef.toString())
                .setHoldTtlSeconds((int) ttl.toSeconds())
                .build());
    }

    @Retry(name = "inventory")
    @CircuitBreaker(name = "inventory")
    public SeatOperationResponse confirm(UUID holdRef) {
        return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                .confirmSeats(HoldRefRequest.newBuilder().setHoldRef(holdRef.toString()).build());
    }

    @Retry(name = "inventory")
    @CircuitBreaker(name = "inventory")
    public SeatOperationResponse release(UUID holdRef) {
        return stub.withDeadlineAfter(3, TimeUnit.SECONDS)
                .releaseSeats(HoldRefRequest.newBuilder().setHoldRef(holdRef.toString()).build());
    }
}
