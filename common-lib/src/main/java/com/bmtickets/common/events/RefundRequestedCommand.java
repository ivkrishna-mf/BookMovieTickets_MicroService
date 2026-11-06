package com.bmtickets.common.events;

import java.util.UUID;

/** Command (booking -> payment) used as a saga compensation, e.g. payment captured after booking expired. */
public record RefundRequestedCommand(UUID bookingId, String reason) {
}
