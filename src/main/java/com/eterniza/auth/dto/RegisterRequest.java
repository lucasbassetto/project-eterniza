package com.eterniza.auth.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank(message = "Nome é obrigatório") String name,
        @Email @NotBlank(message = "E-mail é obrigatório") String email,
        @NotBlank @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres") String password
) {}
