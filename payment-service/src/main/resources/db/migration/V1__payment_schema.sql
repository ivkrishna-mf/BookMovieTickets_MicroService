CREATE TABLE payment (
    id                  UUID PRIMARY KEY,
    booking_id          UUID NOT NULL,
    amount              NUMERIC(12, 2) NOT NULL,
    currency            VARCHAR(3) NOT NULL,
    customer_email      VARCHAR(320),
    status              VARCHAR(30) NOT NULL,
    provider_payment_id VARCHAR(100),
    client_secret       VARCHAR(500),
    provider_refund_id  VARCHAR(100),
    last_error          VARCHAR(500),
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_payment_booking UNIQUE (booking_id),          -- exactly one payment per booking
    CONSTRAINT uk_payment_provider UNIQUE (provider_payment_id)
);
