package com.eterniza.event.dto;

import com.eterniza.event.domain.EventStatus;
import com.eterniza.event.domain.FilmStyle;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id, String name, String slug, String qrCodeUrl,
        FilmStyle filmStyle, EventStatus status, Instant revealAt,
        int guestLimit, int guestCount, int photoCount, Instant createdAt
) {}
