# Fase 8 Validation

**Date**: 2026-07-14
**Spec**: `.specs/features/fase8-testes-integracao/spec.md`
**Verifier**: independent analysis (author ≠ verifier)

---

## Blocker resolved: Testcontainers inside the Maven build container

The Fase 6 report deferred the Photo HTTP layer because Testcontainers could not
find Docker from inside the `maven:3.9-eclipse-temurin-21` build container
("Could not find a valid Docker environment"). Two changes to the run command
fixed this — no production or test-config change required:

1. **Mount the host Docker socket**: `-v /var/run/docker.sock:/var/run/docker.sock`
   (Docker Desktop for Windows treats this path specially). Testcontainers now
   drives the host daemon and starts an ephemeral Postgres as a sibling container.
2. **`TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`**: the ephemeral Postgres
   port is published on the host, not on the build container's `localhost`. This
   env var tells Testcontainers to hand the JDBC URL `host.docker.internal:<port>`
   to the app so the sibling container is reachable.

Result: the two previously-erroring integration tests (`AuthControllerTest`,
`EventControllerTest`) now pass, and the new `PhotoControllerTest` runs green.

---

## Spec-Anchored Acceptance Criteria

| AC | Spec-defined outcome | `file` + assertion | Result |
|---|---|---|---|
| IT-PHOTO-01: upload happy path | 202 ACCEPTED, `data.photoId`/`data.message` present | `PhotoControllerTest.upload_validMultipart...` — `status().isAccepted()` + jsonPath | ✅ PASS |
| IT-PHOTO-01: Photo persisted | status PROCESSING, correct eventId/deviceId/name/originalKey | same test — `photoRepository.findAll()` asserts all fields, `originalKey` startsWith/endsWith | ✅ PASS |
| IT-PHOTO-01: external I/O isolated + delegated | storage upload + rabbit publish invoked | same test — `verify(storageService).upload(...)`, `verify(rabbitTemplate).convertAndSend(...)` | ✅ PASS |
| IT-PHOTO-02: no auth → 401 | Security chain blocks before controller | `upload_withoutAuthorizationHeader...` — `status().isUnauthorized()` + `findAll()` empty | ✅ PASS |
| IT-PHOTO-03: empty file → 400 | BusinessException "Arquivo vazio" through real stack | `upload_emptyFile...` — `status().isBadRequest()` + `$.message`="Arquivo vazio" | ✅ PASS |
| IT-PHOTO-03: invalid type → 400 | "Formato inválido. Envie JPEG, PNG ou WebP" | `upload_invalidContentType...` — 400 + exact message | ✅ PASS |
| IT-PHOTO-04: gallery public + not revealed | 200, revealed=false, empty urls, totalPhotos=count | `gallery_notRevealedEvent...` — 200 + jsonPath | ✅ PASS |
| IT-PHOTO-04: gallery revealed | revealed=true, urls (filtered preferred, original fallback) | `gallery_revealedEvent...` — `containsInAnyOrder("filt-1","orig-2")` | ✅ PASS |

**Status**: ✅ 8/8 ACs covered

---

## Discrimination Sensor (re-run empirically)

| Mutation | File | Description | Test | Failures observed | Result |
|---|---|---|---|---|---|
| 1 | `PhotoController` | `@ResponseStatus(ACCEPTED)` → `OK` | PhotoControllerTest | 1 (`Status expected:<202> but was:<200>`) | ✅ **Killed** |

The mutation was written into the controller, the suite was run inside the
container, the failure was recorded, then reverted. This confirms the
integration test exercises the real HTTP status through the full Spring stack
(not a mocked controller). The 401/400/gallery assertions are likewise
status-and-body driven through the real security + exception-handling chain.

---

## Gate Check

- **Test runner**: Maven + Docker (`maven:3.9-eclipse-temurin-21`) with host Docker socket mounted
- **Command**: see spec "Execution"
- **Result**: **90 passed, 0 failed, 0 errors** (full suite, `mvn test`)
- **Full suite before Fase 8**: BUILD FAILURE — 65 run, 2 errors (AuthControllerTest, EventControllerTest could not start Testcontainers)
- **Full suite after Fase 8**: BUILD SUCCESS — 90 run, 0 errors
- **Delta**: +6 PhotoControllerTest, +19 (the two integration tests that now actually execute: AuthControllerTest 12, EventControllerTest 9 — previously errored at startup)

Per-class (all green): AuthControllerTest 12, EventControllerTest 9,
PhotoControllerTest 6, PhotoServiceTest 7, PhotoProcessingConsumerTest 3,
FilmFilterServiceTest 5, EventServiceTest 13, AuthServiceTest 6, JwtAuthFilterTest 7,
JwtUtilTest 7, GlobalExceptionHandlerTest 5, ApiResponseTest 4,
RevealNotificationConsumerTest 3, EmailServiceTest 2, RevealSchedulerTest 1.

---

## Code Quality

| Principle | Status | Evidence |
|---|---|---|
| No features beyond spec | ✅ | Only added PhotoControllerTest + Fase 8 spec; no production change |
| Matches existing patterns | ✅ | Same Testcontainers/@SpringBootTest/@AutoConfigureMockMvc shape as Auth/EventControllerTest |
| External I/O isolated | ✅ | `@MockBean StorageService` (R2) and `@MockBean RabbitTemplate` (broker) — real controller/service/DB otherwise |
| Tests map to AC | ✅ | 6 tests cover 8 ACs; assertions derive from spec outcomes (status codes, messages, persisted state) |
| Data safety | ✅ | Ephemeral Testcontainers Postgres — dev compose DB untouched |

---

## Summary

**Overall**: ✅ **PASS** — Fase 8 closes the Fase 6 HTTP-layer gap. The Testcontainers
Docker-in-Docker blocker is resolved via socket mount + host override (run-command
only, zero code change). `PhotoControllerTest` exercises the real upload/gallery
HTTP flow (202, 401, 400, visibility) with external I/O mocked; discrimination
sensor 1/1 killed. Whole build is green for the first time: **90/90**.

**Remaining**: none for this scope. The async photo-processing pipeline
(RabbitMQ consumer → R2 → ImageMagick → status) remains covered at the unit level
(PhotoProcessingConsumerTest); an end-to-end broker+R2 integration test is out of
scope for Fase 8 and would require live RabbitMQ + R2 wiring.
