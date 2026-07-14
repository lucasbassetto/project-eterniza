---
name: Fase 6 — Pacote photo
description: Photo upload, storage, and async film filter processing
---

## Overview

Fase 6 implements the photo upload and gallery system for Eterniza. Guests upload photos to events, which are stored in Cloudflare R2, processed with film filters asynchronously via RabbitMQ, and served through a gallery endpoint with event reveal-state visibility control.

## Acceptance Criteria

### PHOTO-01: Guest upload flow
- POST `/api/photos/upload` accepts multipart file + eventId + guest JWT token
- Guest device ID extracted from JWT subject claim
- Guest display name extracted from JWT "displayName" claim
- Photo stored in R2 at `events/{eventId}/originals/{photoId}.jpg`
- Photo record created with status=PROCESSING
- Upload message published to RabbitMQ PHOTO_QUEUE with (photoId, originalKey, filmStyle)
- Returns 202 ACCEPTED with photoId and "Foto recebida!" message
- Empty file rejected with 400 + "Arquivo vazio"
- Invalid content type (not jpeg/png/webp) rejected with 400 + "Formato inválido..."
- File size > 20MB rejected with 400 + "Arquivo muito grande..."
- Missing Authorization header returns 401

### PHOTO-02: Async photo processing
- PhotoProcessingConsumer listens to PHOTO_QUEUE
- Downloads original from R2
- Applies FilmFilterService based on event's filmStyle
- ImageMagick commands:
  - VINTAGE: modulate 100,80,100 + colorize 10,5,0 + contrast-stretch 0.5%
  - BLACK_WHITE: colorspace Gray + contrast-stretch 1%
  - COOL: modulate 100,90,105 + colorize 0,3,12
  - ORIGINAL: passthrough (no filter)
- Filtered image uploaded to R2 at `events/{eventId}/filtered/{photoId}.jpg`
- Photo record updated: filteredKey = filtered path, status = READY
- On error: status = FAILED, original key retained
- Failures logged but do not crash consumer

### PHOTO-03: Gallery endpoint visibility
- GET `/api/photos/gallery/{eventId}` is public (no auth required)
- Query: List all photos with status=READY for the event
- If event status != REVEALED:
  - Return GalleryResponse(revealed=false, totalPhotos=count, photoUrls=[])
  - Hides URLs from non-hosts until reveal
- If event status == REVEALED:
  - Return GalleryResponse(revealed=true, totalPhotos=count, photoUrls=[...])
  - URLs prefer filteredKey if present, fall back to originalKey
- No photos: returns totalPhotos=0, empty urls array

### PHOTO-04: Data integrity
- Photo entity includes: id (UUID), eventId, guestDeviceId, guestName, originalKey, filteredKey (nullable), status (PROCESSING|READY|FAILED), createdAt
- PhotoStatus enum persisted as VARCHAR(50) (Hibernate compatibility, not PostgreSQL ENUM)
- Photos table indexed on (eventId, status) for gallery queries
- Foreign key: eventId → events(id)

### PHOTO-05: R2 storage configuration
- StorageService configured via @Value:
  - eterniza.r2.endpoint (Cloudflare R2 endpoint)
  - eterniza.r2.access-key / secret-key
  - eterniza.r2.bucket
  - eterniza.r2.public-url (serves filtered/original URLs)
- AWS S3 SDK v2 with custom endpoint override
- Upload methods: MultipartFile or byte[] + contentType

## Dependencies

- Fase 5: Event entity, EventStatus, FilmStyle, EventRepository
- RabbitMQConfig: EXCHANGE, PHOTO_QUEUE, PHOTO_KEY, REVEAL_QUEUE constants
- JwtUtil: extractClaims, generateGuestToken methods
- ImageMagick: `convert` command-line tool (for film filter processing)

## Test Coverage

- **PhotoServiceTest**: upload validation (empty, invalid type, file size), gallery visibility (revealed vs not), URL fallback (filteredKey → originalKey)
- **PhotoControllerTest**: integration test with testcontainers PostgreSQL, multipart upload, response codes, gallery endpoint with event reveal state
