package com.eterniza.photo.dto;

import java.util.UUID;

// photosRemaining: quantas fotos o convidado ainda pode enviar neste evento —
// o app usa para mostrar o contador de poses sem precisar de outra chamada.
public record PhotoUploadResponse(UUID photoId, String message, int photosRemaining) {}
