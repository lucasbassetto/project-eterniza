package com.eterniza.event.dto;

import com.eterniza.event.domain.EventStatus;

import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id, String name, String slug, String qrCodeUrl,
        EventStatus status, Instant revealAt,
        int photoCount, Instant createdAt
) {}
