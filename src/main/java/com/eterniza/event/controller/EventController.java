package com.eterniza.event.controller;

import com.eterniza.common.dto.ApiResponse;
import com.eterniza.common.security.JwtUtil;
import com.eterniza.event.dto.CreateEventRequest;
import com.eterniza.event.dto.EventResponse;
import com.eterniza.event.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Gerenciamento de eventos")
public class EventController {

    private final EventService eventService;
    private final JwtUtil jwtUtil;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar evento")
    public ApiResponse<EventResponse> create(
            @Valid @RequestBody CreateEventRequest req,
            @RequestHeader("Authorization") String auth) {
        UUID hostId = UUID.fromString(jwtUtil.extractSubject(auth.replace("Bearer ", "")));
        return ApiResponse.ok("Evento criado", eventService.create(req, hostId));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Buscar evento pelo slug do QR code")
    public ApiResponse<EventResponse> findBySlug(@PathVariable String slug) {
        return ApiResponse.ok(eventService.findBySlug(slug));
    }

    @GetMapping("/my")
    @Operation(summary = "Listar meus eventos")
    public ApiResponse<List<EventResponse>> myEvents(
            @RequestHeader("Authorization") String auth) {
        UUID hostId = UUID.fromString(jwtUtil.extractSubject(auth.replace("Bearer ", "")));
        return ApiResponse.ok(eventService.findByHost(hostId));
    }

    @PostMapping("/{id}/reveal")
    @Operation(summary = "Revelar evento agora (somente o host dono)")
    public ApiResponse<EventResponse> reveal(
            @PathVariable UUID id,
            @RequestHeader("Authorization") String auth) {
        UUID hostId = UUID.fromString(jwtUtil.extractSubject(auth.replace("Bearer ", "")));
        return ApiResponse.ok("Evento revelado", eventService.revealAsHost(id, hostId));
    }
}
