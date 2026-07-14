---
name: Fase 6 — Pacote photo
description: Photo upload, storage, and gallery (filter applied client-side)
---

## Overview

Fase 6 implements the photo upload and gallery system for Eterniza. Guests upload photos to events, which are stored in Cloudflare R2 and served through a gallery endpoint with event reveal-state visibility control.

> **⚠️ Atualização de arquitetura (pós-Fase 8):** o filtro deixou de ser aplicado no servidor. Agora o convidado escolhe o filtro **ao vivo no aplicativo** (client-side, estilo Instagram/Snapchat) e envia a imagem já finalizada; o servidor apenas armazena e serve. Em consequência, o pipeline assíncrono de filtro foi removido — `FilmFilterService` (ImageMagick), `PhotoProcessingConsumer` e a fila `PHOTO_QUEUE` não existem mais. A foto é persistida diretamente com `status = READY`. As seções abaixo marcadas como ~~riscadas~~ ou anotadas refletem essa mudança.

## Acceptance Criteria

### PHOTO-01: Guest upload flow
- POST `/api/photos/upload` accepts multipart file + eventId + guest JWT token
- Guest device ID extracted from JWT subject claim
- Guest display name extracted from JWT "displayName" claim
- Photo (already filtered by the app) stored in R2 at `events/{eventId}/originals/{photoId}.jpg`
- Photo record created with **status=READY** (no server-side processing step)
- Returns 202 ACCEPTED with photoId and "Foto recebida!" message
- Empty file rejected with 400 + "Arquivo vazio"
- Invalid content type (not jpeg/png/webp) rejected with 400 + "Formato inválido..."
- File size > 20MB rejected with 400 + "Arquivo muito grande..."
- Missing Authorization header returns 401

### ~~PHOTO-02: Async photo processing~~ (REMOVIDO)

**Removido na mudança de arquitetura.** O filtro agora é aplicado no aplicativo (client-side), então não há processamento assíncrono no servidor. O que existia aqui — `PhotoProcessingConsumer` escutando `PHOTO_QUEUE`, download do R2, `FilmFilterService` com ImageMagick (VINTAGE/BLACK_WHITE/COOL/ORIGINAL), reupload da versão filtrada e transição `PROCESSING → READY/FAILED` — foi todo removido. A foto já chega `READY`.

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
- Upload method: MultipartFile (the `download` and `byte[]` upload were removed with the filter pipeline)

## Dependencies

- Fase 5: Event entity, EventStatus, FilmStyle (agora usado apenas como filtro sugerido/padrão do evento), EventRepository
- JwtUtil: extractClaims, generateGuestToken methods
- ~~RabbitMQ PHOTO_QUEUE / ImageMagick~~ — removidos (filtro é client-side)

## Test Coverage

- **PhotoServiceTest**: upload validation (empty, invalid type, file size), gallery visibility (revealed vs not), URL fallback (filteredKey → originalKey)
- **PhotoControllerTest**: integration test with testcontainers PostgreSQL, multipart upload, response codes, gallery endpoint with event reveal state
