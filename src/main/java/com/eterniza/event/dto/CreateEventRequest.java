package com.eterniza.event.dto;

import jakarta.validation.constraints.*;

import java.time.Instant;

public record CreateEventRequest(
        @NotBlank(message = "Nome do evento é obrigatório") String name,
        @NotNull @Future(message = "Revelação deve ser no futuro") Instant revealAt
) {}
