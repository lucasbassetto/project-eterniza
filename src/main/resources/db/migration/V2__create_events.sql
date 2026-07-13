CREATE TABLE events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_id      UUID         NOT NULL,
    name         VARCHAR(255) NOT NULL,
    slug         VARCHAR(36)  NOT NULL UNIQUE,
    film_style   VARCHAR(50)  NOT NULL DEFAULT 'VINTAGE',
    status       VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    reveal_at    TIMESTAMP    NOT NULL,
    guest_limit  INT          NOT NULL DEFAULT 5,
    guest_count  INT          NOT NULL DEFAULT 0,
    photo_count  INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_host_id   ON events(host_id);
CREATE INDEX idx_events_slug      ON events(slug);
CREATE INDEX idx_events_reveal_at ON events(reveal_at) WHERE status = 'ACTIVE';
