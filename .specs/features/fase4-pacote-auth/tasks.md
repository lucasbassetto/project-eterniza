# Fase 4 — Pacote auth Tasks

## Execution Protocol (MANDATORY -- do not skip)

Implement these tasks with the `tlc-spec-driven` skill: **activate it by name and follow its Execute flow and Critical Rules.** Do not search for skill files by filesystem path. The skill is the source of truth for the full flow (per-task cycle, sub-agent delegation, adequacy review, Verifier, discrimination sensor).

**If the skill cannot be activated, STOP and tell the user — do not proceed without it.**

---

**Spec**: `.specs/features/fase4-pacote-auth/spec.md`
**Status**: Approved

---

## Test Coverage Matrix

> Generated from codebase sampling (existing tests: `ApiResponseTest`, `GlobalExceptionHandlerTest`, `JwtUtilTest` — all plain unit tests, no Spring context, Mockito for collaborators) and spec.md ACs. Guidelines found: none — strong default applied, floored by existing test depth.

| Code Layer | Required Test Type | Coverage Expectation | Location Pattern | Run Command |
| --- | --- | --- | --- | --- |
| Entity (`Host`) | none | build gate only | — | build gate only |
| Repository (`HostRepository`) | none | Spring Data derived-query interface with no custom logic — testing it would test the framework, not project code (anti-pattern per Check C); exercised indirectly through `AuthService` unit tests via mock | — | build gate only |
| DTOs (records) | none | Validation annotations exercised through `AuthController` integration tests (T6), not standalone | — | build gate only |
| Service (`AuthService`) | unit | All branches; 1:1 to spec ACs (AUTH-01/02/04, AUTH-05/06/07/08, AUTH-09/11); every listed edge case (duplicate-email race note is DB-level, not service-level, so N/A here) | `src/test/java/com/eterniza/auth/service/*.java` | `mvn test -o -Dtest=AuthServiceTest` |
| Security filter (`JwtAuthFilter`) | unit | Filter behavior in isolation: valid/invalid/absent Bearer token, chain.doFilter called or not (AUTH-13/14) | `src/test/java/com/eterniza/auth/security/*.java` | `mvn test -o -Dtest=JwtAuthFilterTest` |
| Controller + full security chain (`AuthController`, `SecurityConfig`) | integration (`@SpringBootTest` + `MockMvc`) | All 3 routes: happy path + every listed edge case + error paths; route protection ACs (AUTH-12/15/16) verified end-to-end through the real filter chain | `src/test/java/com/eterniza/auth/controller/*.java` | `mvn test -o -Dtest=AuthControllerTest` |

## Gate Check Commands

> Generated from codebase — pom.xml has no wrapper (`mvnw`), global `mvn` used in Fase 2/3. `-o` (offline) once dependencies are cached; drop `-o` if a new artifact needs downloading.

| Gate Level | When to Use | Command |
| --- | --- | --- |
| Quick | After tasks with unit tests only | `mvn test -o -Dtest=<TestClass>` |
| Full | After tasks with integration tests | `mvn test -o -Dtest=<TestClass>` (integration tests here still run under Surefire, no separate e2e runner in this repo) |
| Build | After phase completion or config/entity-only tasks | `mvn clean verify -o` |

---

## Execution Plan

### Phase 1: Foundation

```
T1 → T2 → T3
```

### Phase 2: Core Implementation

```
T4 → T5
```

### Phase 3: Integration

```
T6
```

---

## Task Breakdown

### T1: Host entity

**What**: Criar `Host.java` — entidade JPA mapeada para a tabela `hosts` (já existe via migration V1 da Fase 2), com `id`, `email`, `password`, `name`, `createdAt`, `updatedAt`.
**Where**: `src/main/java/com/eterniza/auth/domain/Host.java`
**Depends on**: None
**Reuses**: Padrão `@CreationTimestamp`/`@UpdateTimestamp` + Lombok já usado no restante do projeto
**Requirement**: suporta AUTH-01/04/05/08 (não testável isoladamente)

**Tools**:
- MCP: NONE
- Skill: NONE

**Done when**:
- [ ] Entidade mapeia exatamente as colunas da tabela `hosts` (V1 migration)
- [ ] Compila sem erros
- [ ] Gate check passa: `mvn clean verify -o`

**Tests**: none
**Gate**: build

---

### T2: HostRepository

**What**: Criar `HostRepository.java` — interface Spring Data JPA com `findByEmail` e `existsByEmail`.
**Where**: `src/main/java/com/eterniza/auth/repository/HostRepository.java`
**Depends on**: T1
**Reuses**: Nenhum outro repository existe ainda no projeto (primeiro do pacote)

**Tools**:
- MCP: NONE
- Skill: NONE

**Done when**:
- [ ] Interface estende `JpaRepository<Host, UUID>`
- [ ] Métodos `findByEmail(String)` e `existsByEmail(String)` declarados
- [ ] Gate check passa: `mvn clean verify -o`

**Tests**: none
**Gate**: build

---

### T3: DTOs de auth

**What**: Criar os 4 records — `RegisterRequest`, `LoginRequest`, `AuthResponse`, `GuestSessionRequest` — com as validações Bean Validation exatas do BACKEND_SPEC.md (§4.3).
**Where**: `src/main/java/com/eterniza/auth/dto/RegisterRequest.java`, `LoginRequest.java`, `AuthResponse.java`, `GuestSessionRequest.java`
**Depends on**: None
**Reuses**: Nenhum DTO existente reaproveitável (primeiro pacote com DTOs de request)

**Tools**:
- MCP: NONE
- Skill: NONE

**Done when**:
- [ ] `RegisterRequest`: `name` (`@NotBlank`), `email` (`@Email @NotBlank`), `password` (`@NotBlank @Size(min=8)`)
- [ ] `LoginRequest`: `email` (`@Email @NotBlank`), `password` (`@NotBlank`)
- [ ] `AuthResponse`: `token`, `name`, `email` (sem validação, é resposta)
- [ ] `GuestSessionRequest`: `displayName` (`@NotBlank @Size(max=30)`), `eventId` (`@NotBlank`), `deviceId` (`@NotBlank`)
- [ ] Gate check passa: `mvn clean verify -o`

**Tests**: none (validação exercida em T6)
**Gate**: build

---

### T4: AuthService

**What**: Implementar `register`, `login`, `createGuestSession` — usando `HostRepository`, `PasswordEncoder` (BCrypt) e `JwtUtil` (já existente e validado na Fase 3).
**Where**: `src/main/java/com/eterniza/auth/service/AuthService.java`, `src/test/java/com/eterniza/auth/service/AuthServiceTest.java`
**Depends on**: T1, T2, T3
**Reuses**: `com.eterniza.common.security.JwtUtil`, `com.eterniza.common.exception.BusinessException`/`UnauthorizedException` (Fase 3)
**Requirement**: AUTH-01, AUTH-02, AUTH-04, AUTH-05, AUTH-06, AUTH-07, AUTH-08, AUTH-09, AUTH-11

**Tools**:
- MCP: NONE
- Skill: NONE

**Done when**:
- [ ] `register`: e-mail novo → salva host com senha codificada (BCrypt), retorna `AuthResponse` com token `role=HOST`
- [ ] `register`: e-mail já existente → lança `BusinessException("E-mail já cadastrado")`, SHALL NOT persistir
- [ ] `login`: credenciais corretas → retorna `AuthResponse` com novo token `role=HOST`
- [ ] `login`: e-mail inexistente → lança `UnauthorizedException("Credenciais inválidas")`
- [ ] `login`: senha incorreta → lança `UnauthorizedException("Credenciais inválidas")` (mesma mensagem do caso anterior)
- [ ] `createGuestSession`: retorna token com `subject=deviceId`, `role=GUEST`, claims `displayName`/`eventId`
- [ ] Gate check passa: `mvn test -o -Dtest=AuthServiceTest`
- [ ] Test count: 6 testes passam (sem exclusões silenciosas)

**Nota de correção (spec-precision gap):** a AC original previa "duas chamadas retornam tokens distintos"; removida após falha real de teste — `JwtUtil` usa `iat`/`exp` com granularidade de segundo e sem `jti`, então duas chamadas no mesmo segundo produzem tokens idênticos. Decisão do usuário: corrigir a spec, não adicionar `jti` ao `JwtUtil` (fora de escopo da Fase 4). Ver spec.md Assumptions.

**Tests**: unit
**Gate**: quick

**Commit**: `feat(auth): add AuthService with host register/login and guest session`

---

### T5: SecurityConfig + JwtAuthFilter

**What**: Implementar `SecurityConfig` (rotas públicas, CSRF desabilitado, sessão STATELESS, `PasswordEncoder` bean) e `JwtAuthFilter` (valida Bearer token, 401 explícito se inválido, passa adiante se ausente ou válido).
**Where**: `src/main/java/com/eterniza/auth/security/SecurityConfig.java`, `src/test/java/com/eterniza/auth/security/JwtAuthFilterTest.java`
**Depends on**: None (usa apenas `JwtUtil` do pacote `common`, já validado)
**Reuses**: `com.eterniza.common.security.JwtUtil`
**Requirement**: AUTH-13, AUTH-14 (comportamento isolado do filtro); AUTH-12/15/16 ficam sob responsabilidade do teste de integração em T6, pois dependem da cadeia completa do Spring Security

**Tools**:
- MCP: NONE
- Skill: NONE

**Done when**:
- [ ] `JwtAuthFilter`: sem header `Authorization` → `chain.doFilter` é chamado (passa adiante), resposta não é 401 pelo filtro
- [ ] `JwtAuthFilter`: header presente mas sem prefixo "Bearer " → `chain.doFilter` é chamado (passa adiante) — o filtro só age sobre headers "Bearer "
- [ ] `JwtAuthFilter`: header "Bearer `<token inválido>`" → `response.setStatus(401)`, `chain.doFilter` NÃO é chamado
- [ ] `JwtAuthFilter`: header "Bearer `<token válido>`" → `chain.doFilter` é chamado, status não é setado para 401
- [ ] `SecurityConfig` compila e registra as rotas públicas exatas do BACKEND_SPEC.md §4.5
- [ ] Gate check passa: `mvn test -o -Dtest=JwtAuthFilterTest`
- [ ] Test count: 4 testes passam (sem exclusões silenciosas)

**Tests**: unit
**Gate**: quick

**Commit**: `feat(auth): add SecurityConfig with stateless JWT filter chain`

---

### T6: AuthController + verificação end-to-end da cadeia de segurança

**What**: Implementar `AuthController` (3 endpoints) e escrever testes de integração (`@SpringBootTest` + `MockMvc`) que sobem o contexto real (Controller + Service + SecurityConfig + JwtAuthFilter + GlobalExceptionHandler da Fase 3), cobrindo os fluxos completos das 4 histórias P1, incluindo a proteção de rotas fim-a-fim (AUTH-12/15/16) — que só é observável com a cadeia real do Spring Security montada.
**Where**: `src/main/java/com/eterniza/auth/controller/AuthController.java`, `src/test/java/com/eterniza/auth/controller/AuthControllerTest.java`
**Depends on**: T4, T5
**Reuses**: `com.eterniza.common.dto.ApiResponse`, `com.eterniza.common.exception.GlobalExceptionHandler` (Fase 3)
**Requirement**: AUTH-01, AUTH-02, AUTH-03, AUTH-05, AUTH-06, AUTH-07, AUTH-09, AUTH-10, AUTH-12, AUTH-15, AUTH-16

**Tools**:
- MCP: NONE
- Skill: NONE

**Done when**:
- [ ] `POST /api/auth/register` payload válido → 201 + `ApiResponse` com token/nome/e-mail
- [ ] `POST /api/auth/register` e-mail duplicado → 400 + mensagem "E-mail já cadastrado"
- [ ] `POST /api/auth/register` payload inválido (nome vazio, e-mail malformado, senha curta) → 400 + mensagens de validação unidas por ", "
- [ ] `POST /api/auth/login` credenciais corretas → 200 + token
- [ ] `POST /api/auth/login` e-mail inexistente → 401 + "Credenciais inválidas"
- [ ] `POST /api/auth/login` senha errada → 401 + "Credenciais inválidas"
- [ ] `POST /api/auth/guest/session` payload válido → 200 + token
- [ ] `POST /api/auth/guest/session` payload inválido (nome vazio) → 400 + mensagem de validação
- [ ] Rota pública (`/api/auth/login`) sem header `Authorization` → não retorna 401/403 pela cadeia de segurança
- [ ] Rota protegida hipotética (endpoint de teste ou uma das rotas não-públicas já registradas) sem header `Authorization` → cadeia de segurança bloqueia o acesso (status observado documentado no teste — Spring Security 6 sem `exceptionHandling` customizado; valor exato confirmado empiricamente na implementação, já que o BACKEND_SPEC.md não customiza o entry point)
- [ ] Rota protegida com Bearer token válido → requisição é processada normalmente
- [ ] Resposta não contém cookie de sessão (`Set-Cookie` com `JSESSIONID`) — confirma política STATELESS
- [ ] Gate check passa: `mvn clean verify -o`
- [ ] Test count: 12 testes passam (sem exclusões silenciosas)

**Tests**: integration
**Gate**: build

**Commit**: `feat(auth): add AuthController with register, login and guest session endpoints`

---

## Phase Execution Map

```
Phase 1 → Phase 2 → Phase 3

Phase 1:  T1 ──→ T2 ──→ T3
Phase 2:  T4 ──→ T5
Phase 3:  T6
```

Execution is strictly sequential. Single batch (6 tasks, ≤ ~8) — no sub-agent delegation, executed inline in the main window.

---

## Task Granularity Check

| Task | Scope | Status |
| --- | --- | --- |
| T1: Host entity | 1 file, 1 component | ✅ Granular |
| T2: HostRepository | 1 file, 1 component | ✅ Granular |
| T3: 4 DTO records | 4 small cohesive files, same concept (auth request/response shapes) | ✅ Granular (cohesive) |
| T4: AuthService | 1 file, 1 component (3 related methods) | ✅ Granular |
| T5: SecurityConfig + JwtAuthFilter | 1 file (filter is a static inner class per BACKEND_SPEC.md), 1 cohesive security concern | ✅ Granular (cohesive) |
| T6: AuthController | 1 file, 1 component (3 endpoints of the same resource) | ✅ Granular |

---

## Diagram-Definition Cross-Check

| Task | Depends On (task body) | Diagram Shows | Status |
| --- | --- | --- | --- |
| T1 | None | — | ✅ Match |
| T2 | T1 | T1 → T2 | ✅ Match |
| T3 | None | — (parallel to T1/T2 within Phase 1, executed in listed order) | ✅ Match |
| T4 | T1, T2, T3 | Phase 1 → Phase 2 (T4 first) | ✅ Match |
| T5 | None | Phase 2 (T4 → T5, sequential execution order, no data dependency) | ✅ Match |
| T6 | T4, T5 | Phase 2 → Phase 3 | ✅ Match |

---

## Test Co-location Validation

| Task | Code Layer Created/Modified | Matrix Requires | Task Says | Status |
| --- | --- | --- | --- | --- |
| T1: Host entity | Entity | none | none | ✅ OK |
| T2: HostRepository | Repository | none | none | ✅ OK |
| T3: DTOs | DTOs | none | none | ✅ OK |
| T4: AuthService | Service | unit | unit | ✅ OK |
| T5: SecurityConfig + JwtAuthFilter | Security filter | unit | unit | ✅ OK |
| T6: AuthController | Controller + full security chain | integration | integration | ✅ OK |

---

## Notes

- **Sub-agent delegation**: 6 tasks total → single batch (≤ ~8) → executed inline, no sub-agent offer needed.
- **Tools**: no MCP or skill dependency identified for this feature beyond the `tlc-spec-driven` skill itself; Spring Security exception-translation nuance for AUTH-12/AUTH-13 (401 vs 403 default) will be confirmed empirically in T6 rather than assumed, per the Knowledge Verification Chain.
