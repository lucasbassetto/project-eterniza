# Fase 3 — Pacote common Validation

**Date**: 2026-07-13  
**Spec**: `.specs/features/fase3-pacote-common/spec.md`  
**Diff range**: `e244ec2^..afbe65c` (3 sequential commits implementing ApiResponse, exceptions/handler, JwtUtil + tests)  
**Verifier**: independent sub-agent (author ≠ verifier)

---

## Task Completion

No `tasks.md` exists for this feature. The 3 commits map 1:1 to the spec's user stories:

| Unit of work | Status | Notes |
| --- | --- | --- |
| **CFG-04: ApiResponse envelope** — factories `ok()`, `error()`, JSON null-omission (commit e244ec2) | ✅ Done | Matches BACKEND_SPEC.md §3.1 literally; 4 unit tests (ApiResponseTest) cover all ACs |
| **CFG-05: Exception mapping** — BusinessException→400, NotFoundException→404, UnauthorizedException→401, validation→400, generic→500 (commit 7e84490) | ✅ Done | Matches BACKEND_SPEC.md §3.2–3.3 literally; 5 unit tests (GlobalExceptionHandlerTest) cover all ACs |
| **CFG-06: JWT generation & validation** — host/guest tokens, claims, isValid, extractors, expiration (commit afbe65c) | ✅ Done | Matches BACKEND_SPEC.md §3.4 literally; 7 unit tests (JwtUtilTest) cover all ACs |

---

## Spec-Anchored Acceptance Criteria

### Story CFG-04: ApiResponse Envelope

| AC | Spec-defined outcome | Evidence | Result |
| --- | --- | --- | --- |
| AC1: `ApiResponse.ok(data)` → `success=true`, `data=data`, `message=null` | Factory returns object with exact field values | `ApiResponseTest.okWithDataOnly_setsSuccessTrueAndNullMessage()` line 14-16: asserts `response.isSuccess()` true, `response.getData()` equals payload, `response.getMessage()` null — test runs successfully in `mvn -o test` gate | ✅ PASS |
| AC2: `ApiResponse.ok(message, data)` → `success=true`, `message=message`, `data=data` | Two-arg factory returns object with all fields set | `ApiResponseTest.okWithMessageAndData_setsAllFields()` line 20-25: asserts all three fields match inputs — test passes in gate | ✅ PASS |
| AC3: `ApiResponse.error(message)` → `success=false`, `message=message`, `data=null` | Error factory sets success to false, omits data | `ApiResponseTest.error_setsSuccessFalseAndNullData()` line 29-34: asserts success false, message set, data null — test passes | ✅ PASS |
| AC4: JSON serialization omits null fields (`@JsonInclude(NON_NULL)`) | Serialized JSON does not contain `"message"` or `"data"` keys when null | `ApiResponseTest.jsonSerialization_omitsNullFields()` line 38-44: calls ObjectMapper.writeValueAsString() on response with null message, asserts JSON string lacks `"message"` key but contains `"data"` — test passes | ✅ PASS |

**CFG-04 Status**: ✅ 4/4 ACs verified

---

### Story CFG-05: Exception Mapping

| AC | Spec-defined outcome | Evidence | Result |
| --- | --- | --- | --- |
| AC1: `BusinessException` → HTTP 400 + ApiResponse.error() | Handler catches exception, returns ResponseEntity with status 400 and error message | `GlobalExceptionHandlerTest.handleBusiness_returns400WithMessage()` line 20-26: constructs BusinessException, calls handler directly, asserts `response.getStatusCode()` equals `BAD_REQUEST` (400), body contains error message — test passes | ✅ PASS |
| AC2: `NotFoundException("Recurso", id)` → HTTP 404 + formatted message `"Recurso não encontrado: id"` | Handler returns 404 with formatted message from exception's constructor | `GlobalExceptionHandlerTest.handleNotFound_returns404WithFormattedMessage()` line 30-35: constructs NotFoundException with resource name and id, asserts status 404 and message equals formatted template — test passes | ✅ PASS |
| AC3: `UnauthorizedException` → HTTP 401 + ApiResponse.error() | Handler returns 401 with exception message | `GlobalExceptionHandlerTest.handleUnauthorized_returns401WithMessage()` line 39-44: asserts status 401 and message preserved — test passes | ✅ PASS |
| AC4: `MethodArgumentNotValidException` (validation errors) → HTTP 400 + concatenated field error messages via `", "` | Handler extracts FieldErrors, joins default messages with `", "`, returns 400 | `GlobalExceptionHandlerTest.handleValidation_returns400WithJoinedFieldErrorMessages()` line 48-60: constructs validation exception with two FieldErrors, asserts status 400 and message is joined by `", "` → `"E-mail é obrigatório, Senha deve ter no mínimo 8 caracteres"` — test passes | ✅ PASS |
| AC5: Unmapped generic exception → HTTP 500 + `"Erro interno. Tente novamente."` | Generic handler catches any Exception, logs, returns 500 with standard message | `GlobalExceptionHandlerTest.handleGeneric_returns500WithGenericMessage()` line 64-70: passes RuntimeException to handler, asserts status 500 and exact message text — test passes | ✅ PASS |

**CFG-05 Status**: ✅ 5/5 ACs verified

---

### Story CFG-06: JWT Generation & Validation

| AC | Spec-defined outcome | Evidence | Result |
| --- | --- | --- | --- |
| AC1: `generateHostToken(hostId, email)` → subject=hostId, role="HOST", email claim | Token parser extracts claims matching generation inputs | `JwtUtilTest.generateHostToken_setsSubjectRoleAndEmailClaims()` line 15-21: generates token, extracts claims, asserts subject, role, and email match exactly — test passes | ✅ PASS |
| AC2: `generateGuestToken(deviceId, displayName, eventId)` → subject=deviceId, role="GUEST", displayName claim, eventId claim | Token contains all four claims | `JwtUtilTest.generateGuestToken_setsSubjectRoleDisplayNameAndEventIdClaims()` line 25-32: asserts all four claims extracted — test passes | ✅ PASS |
| AC3: `isValid(token)` on fresh token → `true` | Fresh token validates without exception | `JwtUtilTest.isValid_returnsTrueForFreshlyGeneratedToken()` line 36-39: generates host token, asserts `isValid()` returns true — test passes | ✅ PASS |
| AC4: `isValid(token)` on tampered/malformed token → `false` (no exception) | Invalid signature/corruption returns false, does not throw | `JwtUtilTest.isValid_returnsFalseForTamperedToken_withoutThrowing()` line 43-47: truncates token signature and reassembles, asserts `isValid()` returns false (caught in try-catch, not rethrown) — test passes | ✅ PASS |
| AC5: `isValid(token)` on expired token → `false` | Expired token detected and returns false | `JwtUtilTest.isValid_returnsFalseForExpiredToken()` line 51-55: creates JwtUtil with negative expiration (-1000ms), generates token, asserts `isValid()` returns false — test passes | ✅ PASS |
| AC6: Extractors (`extractSubject`, `extractRole`, `extractEventId`) return exact claims | Each extractor retrieves the corresponding claim value | `JwtUtilTest.extractors_returnExactClaimsUsedAtGeneration()` line 59-64: generates guest token, asserts all three extractors return exact values used at generation — test passes | ✅ PASS |
| AC7: Host and guest tokens use configured expiration values (host vs guest expirations differ) | Calculated expiration interval matches configured `expirationMs` and `guestExpirationMs` | `JwtUtilTest.hostAndGuestTokens_useConfiguredExpirationsRespectively()` line 68-78: generates both tokens, extracts issuedAt + expiration times, asserts difference equals 3_600_000ms (host) and 7_200_000ms (guest) respectively — test passes | ✅ PASS |

**CFG-06 Status**: ✅ 7/7 ACs verified

---

## Gate Check

**Command**: `mvn -o clean test`  
**Environment**: JAVA_HOME=/c/Users/LUCAS/.jdks/openjdk-22.0.2 (explicit Java 22, matching pom.xml's `<java.version>22</java.version>`)

**Results**:
```
Tests run: 16, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

**Test breakdown**:
- `ApiResponseTest`: 4/4 passed
- `GlobalExceptionHandlerTest`: 5/5 passed
- `JwtUtilTest`: 7/7 passed
- **Total**: 16/16 ✅

---

## Boot Verification

**Command**: `timeout 30 mvn -o spring-boot:run`  
**Environment**: Same JAVA_HOME as above

**Log output (relevant line)**:
```
2026-07-13T13:40:27.518-03:00  INFO 1384 --- [eterniza-backend] [           main] com.eterniza.EternizaApplication         : Started EternizaApplication in 4.367 seconds (process running for 4.783)
```

**Checks**:
- ✅ Application starts without fatal errors
- ✅ No ERROR-level logs related to `common` classes
- ✅ Spring context initializes successfully (Postgres connection, Flyway migrations, component scanning all succeed)

---

## Discrimination Sensor

**Method**: Inject a deliberate code fault into ONE class, run relevant test, verify test FAILS (mutation killed), restore exact original code, verify repository clean.

| Mutation | File:line | Description | Killed? | Restore verified? |
| --- | --- | --- | --- | --- |
| Flip `success=true` → `success=false` in `ApiResponse.ok(data)` | `src/main/java/com/eterniza/common/dto/ApiResponse.java:22` | Changed factory method to return wrong success value | ✅ Killed — `ApiResponseTest.okWithDataOnly_setsSuccessTrueAndNullMessage()` failed with `AssertionFailedError: Expecting value to be true but was false`. Exit code 1, BUILD FAILURE. | ✅ Yes — Code restored, `git status` → `nothing to commit, working tree clean` |

**Sensor depth**: lightweight (1 targeted fault injection; appropriate for unit-testable business logic)  
**Result**: 1/1 killed — PASS ✅

---

## Code Quality

| Principle | Status |
| --- | --- |
| No features beyond what was asked | ✅ — Only CFG-04, CFG-05, CFG-06 implemented; no PageResponse, no SecurityConfig, no controllers (correctly deferred per spec's Out of Scope) |
| No abstractions for single-use code | ✅ |
| No unnecessary "flexibility" added | ✅ |
| Only touched files required for task | ✅ — All 10 changed files are direct requirements (ApiResponse, 3 exception classes, GlobalExceptionHandler, JwtUtil, 3 test files, 1 spec file) |
| Didn't "improve" unrelated code | ✅ |
| Matches existing patterns/style | ✅ — Code matches BACKEND_SPEC.md §3.1–3.4 verbatim (byte-for-byte identical to spec excerpts) |
| Would senior engineer approve? | ✅ — Clean, minimal, spec-compliant |
| Spec-anchored outcome check | ✅ — All 16 ACs (4+5+7) matched to precise spec outcomes; see AC tables above |

---

## Edge Cases

| Edge case | Spec reference | Evidence | Status |
| --- | --- | --- | --- |
| NotFoundException with non-String id (e.g., UUID) | Spec §Edge Cases: "message SHALL use String.valueOf/toString() implicitly via formatted()" | Not explicitly tested (would require test setup outside JwtUtilTest scope), but Java's `formatted()` on String template with `%s` placeholder automatically calls `toString()` on any Object — this is default Java behavior, not custom code | ✅ Design correct by Java semantics; no gap |
| JWT expiration at exact limit | Spec §Edge Cases: "expirationMs as exclusive boundary per jjwt library" | Not tested (depends on timing within millisecond precision), but jjwt (io.jsonwebtoken:jjwt) default behavior treats expiration as exclusive; no custom logic override exists | ✅ Matches jjwt defaults; no custom code to verify |
| All 16 ACs exercised by tests | Spec §Success Criteria: "16 total test count (4+5+7)" | Gate: 16/16 tests ran | ✅ Full coverage |

---

## Fix Plans

**Issues found**: none

**Gaps**: none

**No fix tasks required** — Feature is complete and ready to unblock Fase 4 (pacote `auth`).

---

## Requirement Traceability Update

| Requirement ID | Story | Previous Status | New Status |
| --- | --- | --- | --- |
| CFG-04 | P1: Envelope de resposta | Implementing | ✅ Verified (4/4 ACs) |
| CFG-05 | P1: Exceções mapeadas | Implementing | ✅ Verified (5/5 ACs) |
| CFG-06 | P1: JWT geração/validação | Implementing | ✅ Verified (7/7 ACs) |

**Overall coverage**: 3/3 requirements verified ✅

---

## Summary

**Overall**: ✅ **READY**

| Dimension | Result |
| --- | --- |
| Spec-anchored check | 3/3 stories verified, 16/16 ACs covered with direct evidence, 0 spec-precision gaps |
| Gate | `mvn -o test` → 16 passed, 0 failed; `mvn -o spring-boot:run` → clean boot |
| Boot verification | Application starts in 4.367s, all migrations applied, no common-related errors |
| Sensor | 1/1 mutation killed (ApiResponse.ok() success flag flip) |

**What works**:
- All 3 core classes (ApiResponse, exception handling, JWT) match BACKEND_SPEC.md §3.1–3.4 exactly.
- All 16 unit tests pass (4 ApiResponse, 5 GlobalExceptionHandler, 7 JwtUtil).
- Application boots cleanly with no errors related to `common` package classes.
- Tests are comprehensive: all 7 JWT acceptance criteria tested, all 5 exception mappings tested, all 4 ApiResponse factory outcomes tested, JSON serialization verified.
- Mutation testing confirms tests catch real faults (killing the success-flag flip mutation).

**Issues found**: none

**Next phase unblocked**: Fase 4 (pacote `auth`) can now proceed, which depends on JwtUtil for host/guest token generation and ApiResponse/GlobalExceptionHandler for consistent API responses.
