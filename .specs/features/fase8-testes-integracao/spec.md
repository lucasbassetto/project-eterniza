---
name: Fase 8 — Testes de Integração
description: HTTP-layer integration tests for the Photo package, closing the Fase 6 controller gap
---

## Overview

Fase 8 closes the integration-test gap flagged in Fase 6: the Photo HTTP layer
(`PhotoController`) was never exercised end-to-end because Testcontainers could
not reach Docker from inside the Maven build container. That blocker is now
resolved by mounting the host Docker socket (`-v /var/run/docker.sock:...`) and
setting `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`, so Testcontainers
spins an ephemeral Postgres reachable from the build container.

Auth and Event flows already have integration coverage (`AuthControllerTest`,
`EventControllerTest`). This phase adds the missing `PhotoControllerTest`.

## Acceptance Criteria

### IT-PHOTO-01: Upload endpoint (happy path)
- `POST /api/photos/upload` with multipart `file`, `eventId` param, and a valid
  guest `Authorization: Bearer <token>` returns **201 CREATED**
- Response body: `success=true`, `data.photoId` present, `data.message` present
- A `Photo` row is persisted with status `READY`, correct `eventId`,
  `guestDeviceId` (from JWT subject), `guestName` (from JWT `displayName`), and
  `originalKey` matching `events/{eventId}/originals/*.jpg`
- External I/O is isolated: `StorageService` (R2) and `RabbitTemplate` are
  `@MockBean`; test verifies the upload was delegated to storage and a message
  was published

### IT-PHOTO-02: Upload rejects missing authentication
- `POST /api/photos/upload` without an `Authorization` header returns **401**
  (blocked by the security chain before reaching the controller)
- No `Photo` row is persisted

### IT-PHOTO-03: Upload validation errors surface through the real stack
- Empty file → **400** with message `Arquivo vazio`
- Invalid content type (e.g. `application/pdf`) → **400** with message
  `Formato inválido. Envie JPEG, PNG ou WebP`

### IT-PHOTO-04: Gallery visibility (public endpoint)
- `GET /api/photos/gallery/{eventId}` is public (no token) → **200**
- Event `ACTIVE` (not revealed): `data.revealed=false`, `data.photoUrls` empty,
  `data.totalPhotos` = count of READY photos
- Event `REVEALED`: `data.revealed=true`, `data.photoUrls` contains the READY
  photos' URLs (filtered key preferred, original as fallback)

## Dependencies

- Fase 6: PhotoController, PhotoService, Photo entity, PhotoRepository
- Fase 5: Event entity/repository, EventStatus
- Fase 4: JwtUtil (guest token), SecurityConfig (route protection)
- Testcontainers 1.19.8 (PostgreSQLContainer) — already on the classpath

## Execution

```
docker run --rm \
  -v /var/run/docker.sock:/var/run/docker.sock \
  -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal \
  -v d:/eterniza-mono:/app -v C:/Users/LUCAS/.m2:/root/.m2 \
  -w /app maven:3.9-eclipse-temurin-21 mvn test
```

## Test Coverage

- **PhotoControllerTest**: upload 201 + persistence, upload 401 (no auth),
  upload 400 (empty / invalid type), gallery public visibility (revealed vs not)
