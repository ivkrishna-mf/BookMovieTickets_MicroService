package com.bmtickets.booking.client;

import com.bmtickets.common.error.BusinessException;
import com.bmtickets.grpc.catalog.v1.CatalogQueryServiceGrpc;
import com.bmtickets.grpc.catalog.v1.GetShowRequest;
import com.bmtickets.grpc.catalog.v1.ShowDetails;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Synchronous gRPC query (the user is waiting and we need prices now). Retry + circuit breaker + deadline. */
@Component
public class CatalogClient {

    @GrpcClient("catalog")
    private CatalogQueryServiceGrpc.CatalogQueryServiceBlockingStub stub;

    @Retry(name = "catalog")
    @CircuitBreaker(name = "catalog")
    public ShowInfo getShow(UUID showId) {
        try {
            ShowDetails d = stub.withDeadlineAfter(1500, TimeUnit.MILLISECONDS)
                    .getShow(GetShowRequest.newBuilder().setShowId(showId.toString()).build());
            Map<String, BigDecimal> prices = new HashMap<>();
            d.getPriceByCategoryMap().forEach((k, v) -> prices.put(k, new BigDecimal(v)));
            return new ShowInfo(showId, d.getEventTitle(), d.getVenueName(), Instant.ofEpochMilli(d.getStartTimeEpochMs()),
                    d.getStatus(), d.getCurrency(), d.getMaxSeatsPerBooking(), prices);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND || e.getStatus().getCode() == Status.Code.INVALID_ARGUMENT) {
                // business outcome: not retried, not counted as a failure by the circuit breaker
                throw new BusinessException(HttpStatus.NOT_FOUND, "SHOW_NOT_FOUND", "Show " + showId + " not found");
            }
            throw e;
        }
    }
}
