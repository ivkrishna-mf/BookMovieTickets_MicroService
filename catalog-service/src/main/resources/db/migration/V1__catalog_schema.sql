CREATE TABLE venue (
    id          UUID PRIMARY KEY,
    name        VARCHAR(200) NOT NULL,
    city        VARCHAR(100) NOT NULL,
    address     VARCHAR(500),
    capacity    INT NOT NULL
);
CREATE INDEX idx_venue_city ON venue (lower(city));

CREATE TABLE event (
    id               UUID PRIMARY KEY,
    title            VARCHAR(300) NOT NULL,
    description      TEXT,
    category         VARCHAR(50)  NOT NULL,
    language         VARCHAR(50),
    duration_minutes INT NOT NULL,
    created_at       TIMESTAMPTZ NOT NULL
);

CREATE TABLE shows (
    id                    UUID PRIMARY KEY,
    event_id              UUID NOT NULL REFERENCES event (id),
    venue_id              UUID NOT NULL REFERENCES venue (id),
    start_time            TIMESTAMPTZ NOT NULL,
    status                VARCHAR(20) NOT NULL,
    currency              VARCHAR(3) NOT NULL,
    max_seats_per_booking INT NOT NULL,
    version               BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_shows_event_start ON shows (event_id, start_time);
CREATE INDEX idx_shows_venue_start ON shows (venue_id, start_time);

CREATE TABLE show_price (
    show_id  UUID NOT NULL REFERENCES shows (id) ON DELETE CASCADE,
    category VARCHAR(50) NOT NULL,
    price    NUMERIC(12, 2) NOT NULL,
    PRIMARY KEY (show_id, category)
);
