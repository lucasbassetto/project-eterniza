package com.eterniza.photo.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Foto do evento vista pelo host (moderação). O host sempre recebe a {@code url}
 * da imagem, mesmo antes da revelação — ele precisa ver o conteúdo para moderar.
 */
public record EventPhotoResponse(
        UUID photoId, String guestName, Instant createdAt, String url
) {}
