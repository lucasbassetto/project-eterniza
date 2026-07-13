package com.eterniza.auth.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password
) {}
