package com.eterniza.photo.controller;

import com.eterniza.common.dto.ApiResponse;
import com.eterniza.event.repository.EventRepository;
import com.eterniza.photo.dto.GalleryResponse;
import com.eterniza.photo.dto.PhotoUploadResponse;
import com.eterniza.photo.service.PhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
@Tag(name = "Photos", description = "Upload e galeria de fotos")
public class PhotoController {

    private final PhotoService photoService;
    private final EventRepository eventRepository;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Enviar foto (guest)")
    public ApiResponse<PhotoUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("eventId") String eventId,
            @RequestHeader("Authorization") String guestToken) throws IOException {
        return ApiResponse.ok(photoService.upload(file, guestToken, eventId));
    }

    @GetMapping("/gallery/{eventId}")
    @Operation(summary = "Galeria do evento")
    public ApiResponse<GalleryResponse> gallery(@PathVariable String eventId) {
        boolean revealed = eventRepository.findById(UUID.fromString(eventId))
                .map(e -> e.getStatus().name().equals("REVEALED"))
                .orElse(false);
        return ApiResponse.ok(photoService.getGallery(eventId, revealed));
    }
}
