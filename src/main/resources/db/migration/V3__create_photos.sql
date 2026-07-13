CREATE TABLE photos (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id         UUID         NOT NULL,
    guest_device_id  VARCHAR(255) NOT NULL,
    guest_name       VARCHAR(255) NOT NULL,
    original_key     VARCHAR(500) NOT NULL,
    filtered_key     VARCHAR(500),
    status           VARCHAR(50) NOT NULL DEFAULT 'PROCESSING',
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_photos_event FOREIGN KEY (event_id) REFERENCES events(id)
);

CREATE INDEX idx_photos_event_id ON photos(event_id);
CREATE INDEX idx_photos_status   ON photos(status);
