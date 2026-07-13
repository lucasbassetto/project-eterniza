package com.eterniza.auth.dto;

import jakarta.validation.constraints.*;

public record GuestSessionRequest(
        @NotBlank(message = "Nome de exibição é obrigatório")
        @Size(max = 30, message = "Nome deve ter no máximo 30 caracteres")
        String displayName,

        @NotBlank(message = "ID do evento é obrigatório")
        String eventId,

        @NotBlank(message = "Device ID é obrigatório")
        String deviceId
) {}
