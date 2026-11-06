-- Transactional outbox (producer side) and inbox / processed events (idempotent consumer side).
CREATE TABLE outbox_event (
    id             UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id   VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    topic          VARCHAR(200) NOT NULL,
    payload        TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL,
    published_at   TIMESTAMPTZ
);
-- partial index keeps the relay query fast no matter how many published rows remain
CREATE INDEX idx_outbox_unpublished ON outbox_event (created_at) WHERE published_at IS NULL;

CREATE TABLE processed_event (
    id           VARCHAR(300) PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL
);
