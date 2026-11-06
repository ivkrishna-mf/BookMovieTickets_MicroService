CREATE TABLE seat (
    id              UUID PRIMARY KEY,
    show_id         UUID NOT NULL,
    seat_number     VARCHAR(20) NOT NULL,
    row_label       VARCHAR(10) NOT NULL,
    seat_index      INT NOT NULL,
    category        VARCHAR(50) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    hold_ref        UUID,
    hold_expires_at TIMESTAMPTZ,
    version         BIGINT NOT NULL DEFAULT 0,
    -- final guard: a seat exists exactly once per show
    CONSTRAINT uk_seat_show_number UNIQUE (show_id, seat_number),
    CONSTRAINT ck_seat_status CHECK (status IN ('AVAILABLE', 'HELD', 'BOOKED'))
);
CREATE INDEX idx_seat_hold_ref ON seat (hold_ref) WHERE hold_ref IS NOT NULL;
CREATE INDEX idx_seat_expired_holds ON seat (hold_expires_at) WHERE status = 'HELD';
