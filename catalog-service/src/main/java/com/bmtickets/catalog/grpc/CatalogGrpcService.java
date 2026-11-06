package com.bmtickets.catalog.grpc;

import com.bmtickets.catalog.api.CatalogDtos.ShowResponse;
import com.bmtickets.catalog.service.CatalogService;
import com.bmtickets.common.error.BusinessException;
import com.bmtickets.grpc.catalog.v1.CatalogQueryServiceGrpc;
import com.bmtickets.grpc.catalog.v1.GetShowRequest;
import com.bmtickets.grpc.catalog.v1.ShowDetails;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.util.UUID;

/** Internal gRPC endpoint (HTTP/2 + protobuf): smaller payloads and lower latency than JSON for the booking hot path. */
@GrpcService
public class CatalogGrpcService extends CatalogQueryServiceGrpc.CatalogQueryServiceImplBase {

    private final CatalogService service;

    public CatalogGrpcService(CatalogService service) {
        this.service = service;
    }

    @Override
    public void getShow(GetShowRequest request, StreamObserver<ShowDetails> observer) {
        try {
            ShowResponse s = service.show(UUID.fromString(request.getShowId()));
            ShowDetails.Builder b = ShowDetails.newBuilder()
                    .setShowId(s.id().toString())
                    .setEventId(s.eventId().toString())
                    .setEventTitle(s.eventTitle())
                    .setVenueName(s.venueName())
                    .setCity(s.city())
                    .setStartTimeEpochMs(s.startTime().toEpochMilli())
                    .setStatus(s.status())
                    .setCurrency(s.currency())
                    .setMaxSeatsPerBooking(s.maxSeatsPerBooking());
            s.priceByCategory().forEach((cat, price) -> b.putPriceByCategory(cat, price.toPlainString()));
            observer.onNext(b.build());
            observer.onCompleted();
        } catch (IllegalArgumentException e) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription("Invalid show id").asRuntimeException());
        } catch (BusinessException e) {
            observer.onError(Status.NOT_FOUND.withDescription(e.getMessage()).asRuntimeException());
        }
    }
}
