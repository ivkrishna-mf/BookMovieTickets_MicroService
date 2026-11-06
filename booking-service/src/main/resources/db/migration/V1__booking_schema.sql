CREATE TABLE booking (
    id                    UUID PRIMARY KEY,
    user_id               VARCHAR(100) NOT NULL,
    user_email            VARCHAR(320),
    show_id               UUID NOT NULL,
    event_title           VARCHAR(300),
    venue_name            VARCHAR(200),
    show_time             TIMESTAMPTZ,
    status                VARCHAR(30) NOT NULL,
    total_amount          NUMERIC(12, 2),
    currency              VARCHAR(3),
    payment_id            UUID,
    payment_client_secret VARCHAR(500),
    idempotency_key       VARCHAR(100) NOT NULL,
    failure_reason        VARCHAR(200),
    ticket_code           VARCHAR(200),
    expires_at            TIMESTAMPTZ,
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0,
    -- idempotency fallback when the Redis record is gone: same user + same key => same booking
    CONSTRAINT uk_booking_user_idempotency UNIQUE (user_id, idempotency_key)
);

-- duplicate-booking guard: at most ONE in-flight booking per user per show (double-click, two tabs, bots)
CREATE UNIQUE INDEX uk_booking_active_user_show ON booking (user_id, show_id)
    WHERE status IN ('PENDING_SEATS', 'PENDING_PAYMENT');

CREATE INDEX idx_booking_user_created ON booking (user_id, created_at DESC);
CREATE INDEX idx_booking_expiry ON booking (expires_at) WHERE status = 'PENDING_PAYMENT';
CREATE INDEX idx_booking_stuck ON booking (created_at) WHERE status = 'PENDING_SEATS';

CREATE TABLE booking_seat (
    booking_id  UUID NOT NULL REFERENCES booking (id) ON DELETE CASCADE,
    position    INT NOT NULL,
    seat_number VARCHAR(20) NOT NULL,
    PRIMARY KEY (booking_id, position)
);
