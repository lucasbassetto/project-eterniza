# LESSONS — auto-maintained by scripts/lessons.py

> Machine-owned. Do NOT hand-edit. Changes are overwritten on the next `lessons.py` write.
> Canonical state lives in `.specs/lessons.json`. Edit lessons only via the script.
> promote_threshold=2 distinct features · window_days=45 · quarantine_threshold=2

## Confirmed (load these at Specify/Design)

Corroborated across multiple features. Safe to apply as guidance.

_none_

## Candidates (under observation — do NOT load as guidance yet)

Seen once or not yet corroborated. Tracked, not trusted.

### L-001 — When the spring-boot-starter-parent BOM bumps Flyway to 10.x, add org.flywaydb:flyway-database-postgresql explicitly - flyway-core alone throws Unsupported Database against Postgres 16+ since PostgreSQL support was split into its own module.
- signal: `spec_deviation` · recurrence: 1 feature(s) · scope: `pom.xml,flyway,persistence` · harmful: 0
- features: fase2-config-projeto-java
- evidence: 63face0:pom.xml SPEC_DEVIATION note; validated by removing the dependency and reproducing the boot failure in validation.md (pom.xml,flyway,persistence)
- last seen: 2026-07-13T16:26:04Z

## Quarantined (failed when applied — ignore)

A confirmed lesson that recurred alongside failure. Kept for the maintainer to review.

_none_
