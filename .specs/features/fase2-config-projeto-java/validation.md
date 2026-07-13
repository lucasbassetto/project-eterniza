# Fase 2 — Configuração do projeto Java Validation

**Date**: 2026-07-13
**Spec**: `.specs/features/fase2-config-projeto-java/spec.md`
**Diff range**: `fb456c0..63face0` (both commits since repo init; fb456c0 added the entry point, 63face0 added the migrations + pom.xml dependency)
**Verifier**: independent sub-agent (author ≠ verifier)

---

## Task Completion

No `tasks.md` exists for this feature (infra-only phase, tracked directly via commit history and STATE.md). Both commits map 1:1 to the spec's implicit task breakdown:

| Unit of work | Status | Notes |
| --- | --- | --- |
| `EternizaApplication.java` entry point (fb456c0) | ✅ Done | Matches BACKEND_SPEC.md §2.1 literally |
| V1/V2/V3 Flyway migrations + pom.xml flyway-database-postgresql dep (63face0) | ✅ Done | Matches BACKEND_SPEC.md §2.3 literally |

---

## Spec-Anchored Acceptance Criteria

| Criterion (WHEN X THEN Y) | Spec-defined outcome | Evidence | Result |
| --- | --- | --- | --- |
| CFG-01: WHEN app is started THEN log `Started EternizaApplication`, no fatal exceptions | Exact log line `Started EternizaApplication in X.XXX seconds`, boot completes | `/tmp/verify-boot1.log:53` (fresh schema) — `Started EternizaApplication in 4.632 seconds (process running for 5.087)`; also reproduced in `/tmp/verify-boot4-final.log` (`4.889 seconds`) after sensor restore. Both boots show zero ERROR lines before this line. | ✅ PASS |
| CFG-02: WHEN app starts for the first time with Postgres available THEN Flyway runs V1, V2, V3 in order, creating `hosts`, `events`, `photos` + `event_status`, `film_style`, `photo_status` | Exactly 3 migrations applied against an empty schema, tables/types created | `/tmp/verify-boot1.log` lines 30-33 (Flyway log, on a schema I dropped with `DROP SCHEMA public CASCADE` beforehand to force a true fresh run): `Migrating schema "public" to version "1 - create hosts"` → `"2 - create events"` → `"3 - create photos"` → `Successfully applied 3 migrations to schema "public", now at version v3`. Independently confirmed via `docker exec eterniza-postgres psql -U eterniza -d eterniza -c "\dt"` → `events, flyway_schema_history, hosts, photos` and `\dT` → `event_status, film_style, photo_status`. Column-level check via `\d hosts`/`\d events`/`\d photos` matches BACKEND_SPEC.md §2.3 exactly (types, defaults, NOT NULL, UNIQUE, FK `fk_photos_event`, partial index `idx_events_reveal_at ... WHERE status = 'ACTIVE'`). | ✅ PASS |
| CFG-03: WHEN app is restarted with migrations already applied THEN Flyway detects no new scripts, does not fail or reapply | No re-migration, no error, clean restart | `/tmp/verify-boot2.log`: `Current version of schema "public": 3` → `Schema "public" is up to date. No migration necessary.` → `Started EternizaApplication in 4.764 seconds`. Zero errors. | ✅ PASS |

**Status**: ✅ All 3 ACs (CFG-01, CFG-02, CFG-03) covered with direct evidence, 0 spec-precision gaps.

---

## Discrimination Sensor

Method: copied `V2__create_events.sql` to a scratch backup, mutated the working copy on disk (never staged/committed), reset the Postgres schema to force a fresh migration run, rebooted, observed the failure, then restored the exact original content and re-verified a clean boot.

| Mutation | File:line | Description | Killed? |
| --- | --- | --- | --- |
| 1 | `src/main/resources/db/migration/V2__create_events.sql:6,19` | Renamed column `host_id` → `host_idd` in the `CREATE TABLE events` statement while leaving `CREATE INDEX idx_events_host_id ON events(host_id)` unchanged, creating a dangling column reference within the same file | ✅ Killed — `/tmp/verify-boot3-mutant.log`: `org.flywaydb.core.internal.command.DbMigrate$FlywayMigrateException: Migration V2__create_events.sql failed` / `SQL State: 42703` / `Message: ERROR: column "host_id" does not exist` / `Line: 19`. App failed to boot (`Application run failed`, Maven exit code 1). |
| 2 (optional, strengthens SPEC_DEVIATION claim) | `pom.xml` (removed `flyway-database-postgresql` dependency, temporarily) | Reproduced the exact failure the author's SPEC_DEVIATION note in commit 63face0 claims motivated adding the dependency | ✅ Killed — `/tmp/verify-pomdeviation.log`: `org.flywaydb.core.api.FlywayException: Unsupported Database: PostgreSQL 16.14`, app failed to boot. Confirms the SPEC_DEVIATION was necessary, not speculative. |

Both mutations were reverted immediately after observation (`cp` from backup for the SQL file, `cp` from backup for `pom.xml`). Final state verified with `git status` → `nothing to commit, working tree clean` and `git diff` → empty, both before and after each mutation cycle. A final clean `mvn -o clean compile` + `spring-boot:run` cycle was run after restoration and confirmed a normal boot (`/tmp/verify-boot4-final.log`, `Started EternizaApplication in 4.889 seconds`, migrations reapplied cleanly to the schema I had dropped for the mutant test).

**Sensor depth**: lightweight (2 targeted fault injections; this is a P1 infra/config feature, not P0 payment/auth/data-integrity, so the default tier applies)
**Result**: 2/2 killed — PASS ✅

---

## Interactive UAT Results

Not performed — this is a backend/infrastructure-only feature (no UI, no user-facing behavior). Per the skill's rule, automated checks (boot verification + schema inspection + sensor) are sufficient for this phase.

---

## Code Quality

| Principle | Status |
| --- | --- |
| No features beyond what was asked | ✅ — entry point and 3 migrations only, no extra packages (auth/event/photo/notification correctly deferred to later phases per spec's Out of Scope) |
| No abstractions for single-use code | ✅ |
| No unnecessary "flexibility" added | ✅ |
| Only touched files required for task | ✅ — `git show --stat` on both commits shows no unrelated file changes |
| Didn't "improve" unrelated code | ✅ |
| Matches existing patterns/style | ✅ — SQL and Java match BACKEND_SPEC.md snippets verbatim (diffed manually, byte-for-byte identical to §2.1 and §2.3) |
| Would senior engineer approve? | ✅ |
| Spec-anchored outcome check | ✅ — see AC table above, all 3 criteria matched to precise spec outcomes |
| Per-layer Coverage Expectation | N/A — no domain logic in this phase (infra/config only), correctly scoped |
| Documented guidelines followed | None documented for this project beyond BACKEND_SPEC.md — strong defaults applied |

---

## Edge Cases

- [x] "Docker not running → app fails with DB connection error" — not independently re-tested (would require stopping the Fase 1 containers, which the task instructions explicitly said to leave running); this is default Spring/HikariCP behavior per the spec's own note ("comportamento padrão... nenhum tratamento especial necessário") and requires no custom code to verify. Not a gap — spec explicitly says no special handling exists to test.
- [x] "App already initialized, schema exists → Flyway idempotent" — Handled correctly, see CFG-03 evidence above.

---

## Gate Check

- **Gate command**: `mvn -o compile` (Build gate — no test suite exists for this migration/config-only phase, per the "no tests → build gate = build only" rule)
- **Result**: BUILD SUCCESS (both `mvn -o compile` on the pre-existing `target/` and a from-scratch `mvn -o clean compile`, which recompiled `EternizaApplication.java` with `[debug parameters release 21]` — confirms the Java 21 `--release` target from `pom.xml`'s `<java.version>21</java.version>` is actually enforced by JDK 22)
- **Test count before/after feature**: N/A — no test suite in this phase (by design, Fase 8 in BACKEND_SPEC.md)
- **Skipped tests**: none (none exist)
- **Failures**: none

---

## Fix Plans

None — no gaps found.

---

## Requirement Traceability Update

| Requirement | Previous Status | New Status |
| --- | --- | --- |
| CFG-01 | Implementing | ✅ Verified |
| CFG-02 | Implementing | ✅ Verified |
| CFG-03 | Implementing | ✅ Verified |

---

## Summary

**Overall**: ✅ Ready

**Spec-anchored check**: 3/3 ACs matched spec outcome, 0 spec-precision gaps
**Sensor**: 2/2 mutations killed
**Gate**: build passed (compile-only, no test suite expected at this phase)

**What works**:
- `EternizaApplication.java` boots cleanly against the live Fase 1 Postgres/Redis/RabbitMQ containers, both on a fresh (dropped) schema and on a pre-existing v3 schema (idempotent restart).
- All 3 Flyway migrations apply in order and produce tables/types matching BACKEND_SPEC.md §2.3 exactly (verified column-by-column via `\d` in psql, not just table existence).
- The `flyway-database-postgresql` SPEC_DEVIATION in `pom.xml` was independently reproduced as necessary: removing it deterministically reproduces `FlywayException: Unsupported Database: PostgreSQL 16.14`.
- No secrets committed — `docker-compose.yml`/`application.yml` only contain the documented local-dev password `eterniza123` and env-var-with-placeholder-default patterns (`sua-api-key-aqui`, `sua-secret-key`, a dev-only JWT secret placeholder), consistent with BACKEND_SPEC.md.

**Issues found**: none

**Next steps**: None required. Feature is ready to unblock Fase 3 (pacote `common`).
