package com.eterniza.event.dto;

import com.eterniza.event.domain.FilmStyle;
import jakarta.validation.constraints.*;

import java.time.Instant;

public record CreateEventRequest(
        @NotBlank(message = "Nome do evento é obrigatório") String name,
        @NotNull(message = "Estilo de filme é obrigatório") FilmStyle filmStyle,
        @NotNull @Future(message = "Revelação deve ser no futuro") Instant revealAt,
        Integer guestLimit
) {}
