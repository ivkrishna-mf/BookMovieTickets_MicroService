package com.bmtickets.inventory.grpc;

import com.bmtickets.grpc.inventory.v1.HeldSeat;
import com.bmtickets.grpc.inventory.v1.HoldRefRequest;
import com.bmtickets.grpc.inventory.v1.HoldSeatsRequest;
import com.bmtickets.grpc.inventory.v1.HoldSeatsResponse;
import com.bmtickets.grpc.inventory.v1.SeatInventoryServiceGrpc;
import com.bmtickets.grpc.inventory.v1.SeatOperationResponse;
import com.bmtickets.inventory.hold.HoldResult;
import com.bmtickets.inventory.hold.SeatReservationService;
import io.grpc.Status;
import io.grpc.stub.StreamObserver;
import net.devh.boot.grpc.server.service.GrpcService;

import java.time.Duration;
import java.util.UUID;

@GrpcService
public class InventoryGrpcService extends SeatInventoryServiceGrpc.SeatInventoryServiceImplBase {

    private final SeatReservationService reservations;

    public InventoryGrpcService(SeatReservationService reservations) {
        this.reservations = reservations;
    }

    @Override
    public void holdSeats(HoldSeatsRequest request, StreamObserver<HoldSeatsResponse> observer) {
        try {
            if (request.getSeatNumbersCount() == 0) {
                throw new IllegalArgumentException("No seats requested");
            }
            HoldResult r = reservations.hold(UUID.fromString(request.getShowId()), request.getSeatNumbersList(),
                    UUID.fromString(request.getHoldRef()), Duration.ofSeconds(Math.max(30, request.getHoldTtlSeconds())));
            HoldSeatsResponse.Builder b = HoldSeatsResponse.newBuilder().setSuccess(r.success())
                    .addAllUnavailableSeats(r.unavailableSeats());
            if (r.failureReason() != null) {
                b.setFailureReason(r.failureReason());
            }
            if (r.expiresAt() != null) {
                b.setHoldExpiresAtEpochMs(r.expiresAt().toEpochMilli());
            }
            r.categoryBySeat().forEach((seat, cat) -> b.addSeats(HeldSeat.newBuilder().setSeatNumber(seat).setCategory(cat)));
            observer.onNext(b.build());
            observer.onCompleted();
        } catch (IllegalArgumentException e) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INTERNAL.withDescription("Hold failed").withCause(e).asRuntimeException());
        }
    }

    @Override
    public void confirmSeats(HoldRefRequest request, StreamObserver<SeatOperationResponse> observer) {
        operation(request, observer, true);
    }

    @Override
    public void releaseSeats(HoldRefRequest request, StreamObserver<SeatOperationResponse> observer) {
        operation(request, observer, false);
    }

    private void operation(HoldRefRequest request, StreamObserver<SeatOperationResponse> observer, boolean confirm) {
        try {
            UUID ref = UUID.fromString(request.getHoldRef());
            int affected = confirm ? reservations.confirm(ref) : reservations.release(ref);
            SeatOperationResponse.Builder b = SeatOperationResponse.newBuilder().setAffected(affected);
            if (confirm && affected == 0) {
                b.setSuccess(false).setFailureReason("HOLD_NOT_FOUND");
            } else {
                b.setSuccess(true);
            }
            observer.onNext(b.build());
            observer.onCompleted();
        } catch (IllegalArgumentException e) {
            observer.onError(Status.INVALID_ARGUMENT.withDescription(e.getMessage()).asRuntimeException());
        } catch (RuntimeException e) {
            observer.onError(Status.INTERNAL.withDescription("Seat operation failed").withCause(e).asRuntimeException());
        }
    }
}
