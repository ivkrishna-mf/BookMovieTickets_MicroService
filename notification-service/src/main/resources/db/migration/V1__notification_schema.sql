CREATE TABLE notification_log (
    id                  UUID PRIMARY KEY,
    booking_id          UUID NOT NULL,
    type                VARCHAR(50) NOT NULL,
    recipient           VARCHAR(320),
    status              VARCHAR(20) NOT NULL,
    provider_message_id VARCHAR(200),
    created_at          TIMESTAMPTZ NOT NULL,
    sent_at             TIMESTAMPTZ,
    CONSTRAINT uk_notification_booking_type UNIQUE (booking_id, type)
);
