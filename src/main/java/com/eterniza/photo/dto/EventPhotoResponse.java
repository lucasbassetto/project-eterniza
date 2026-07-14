package com.eterniza.photo.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Foto do evento vista pelo host (moderação). Antes da revelação a {@code url}
 * vem nula — nem o host vê as imagens, só os metadados para poder moderar.
 */
public record EventPhotoResponse(
        UUID photoId, String guestName, Instant createdAt, String url
) {}
