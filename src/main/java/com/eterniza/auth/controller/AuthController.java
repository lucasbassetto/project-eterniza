package com.eterniza.auth.controller;

import com.eterniza.auth.dto.*;
import com.eterniza.auth.service.AuthService;
import com.eterniza.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Autenticação do host e sessão do guest")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar novo host")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok("Conta criada com sucesso", authService.register(req));
    }

    @PostMapping("/login")
    @Operation(summary = "Login do host")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }

    @PostMapping("/guest/session")
    @Operation(summary = "Criar sessão anônima para guest")
    public ApiResponse<String> guestSession(@Valid @RequestBody GuestSessionRequest req) {
        return ApiResponse.ok(authService.createGuestSession(req));
    }
}
