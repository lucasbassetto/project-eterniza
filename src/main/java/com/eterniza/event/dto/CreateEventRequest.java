package com.eterniza.event.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;

public record CreateEventRequest(
        @NotBlank(message = "Nome do evento é obrigatório") String name,
        @NotNull @Future(message = "Revelação deve ser no futuro") Instant revealAt,
        @Min(value = 1, message = "Limite de fotos por convidado deve ser no mínimo 1")
        @Max(value = 100, message = "Limite de fotos por convidado deve ser no máximo 100")
        Integer photoLimitPerGuest
) {}
