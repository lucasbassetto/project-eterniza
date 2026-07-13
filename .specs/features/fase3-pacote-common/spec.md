# Fase 3 — Pacote common Specification

## Problem Statement

Todos os demais pacotes (auth, event, photo, notification) precisam de um envelope de resposta padrão, exceções de domínio mapeadas para status HTTP, e geração/validação de JWT. Sem isso, nenhum controller pode ser implementado de forma consistente.

## Goals

- [ ] Toda resposta da API pode usar o envelope `ApiResponse<T>` (sucesso e erro)
- [ ] Exceções de negócio são automaticamente traduzidas para o status HTTP correto, no formato `ApiResponse`
- [ ] `JwtUtil` gera e valida tokens JWT para host (autenticado) e guest (anônimo), com expirações diferentes

## Out of Scope

| Item | Motivo |
| --- | --- |
| `SecurityConfig` / filtro JWT | Fase 4 — depende do `JwtUtil` existir primeiro |
| Qualquer controller | Fases 4-7 |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Conteúdo exato das classes | Copiado literalmente do BACKEND_SPEC.md §3.1-3.4 | Spec já define o código exato, sem ambiguidade | y |
| Cobertura de testes | Testes unitários para `ApiResponse` (factories), `GlobalExceptionHandler` (mapeamento exceção→status) e `JwtUtil` (geração/validação/expiração/claims) — não para as classes de exceção (sem lógica, apenas construtores) | Estas 3 classes têm comportamento real testável; exceções são apenas carriers de mensagem. O BACKEND_SPEC §3.5 só pede "app sobe sem erro", mas `JwtUtil` é infraestrutura de segurança usada por todas as fases seguintes — vale testar agora | y |

**Open questions:** nenhuma.

---

## User Stories

### P1: Envelope de resposta padrão ⭐ MVP

**User Story**: Como desenvolvedor de qualquer controller, quero um envelope `ApiResponse<T>` padrão, para que toda resposta da API tenha o mesmo formato (`success`, `message`, `data`, `timestamp`).

**Acceptance Criteria**:

1. WHEN `ApiResponse.ok(data)` é chamado THEN o sistema SHALL retornar um objeto com `success=true`, `data=data`, `message=null`
2. WHEN `ApiResponse.ok(message, data)` é chamado THEN o sistema SHALL retornar um objeto com `success=true`, `message=message`, `data=data`
3. WHEN `ApiResponse.error(message)` é chamado THEN o sistema SHALL retornar um objeto com `success=false`, `message=message`, `data=null`
4. WHEN o objeto é serializado para JSON THEN campos `null` (`message` ou `data`) SHALL ser omitidos (`@JsonInclude(NON_NULL)`)

**Independent Test**: Testes unitários chamando os factory methods e inspecionando os campos do objeto retornado.

---

### P1: Exceções de negócio mapeadas para HTTP ⭐ MVP

**User Story**: Como desenvolvedor de qualquer controller, quero lançar exceções de domínio e ter certeza de que o cliente recebe o status HTTP e formato corretos, sem precisar tratar isso em cada controller.

**Acceptance Criteria**:

1. WHEN uma `BusinessException` é lançada THEN o `GlobalExceptionHandler` SHALL retornar HTTP 400 com `ApiResponse.error(mensagem da exceção)`
2. WHEN uma `NotFoundException("Recurso", id)` é lançada THEN o handler SHALL retornar HTTP 404 com mensagem `"Recurso não encontrado: id"`
3. WHEN uma `UnauthorizedException` é lançada THEN o handler SHALL retornar HTTP 401 com `ApiResponse.error(mensagem)`
4. WHEN uma `MethodArgumentNotValidException` é lançada (validação de `@Valid`) THEN o handler SHALL retornar HTTP 400 com as mensagens de todos os `FieldError`s concatenadas por `", "`
5. WHEN uma exceção genérica não mapeada é lançada THEN o handler SHALL retornar HTTP 500 com `ApiResponse.error("Erro interno. Tente novamente.")`

**Independent Test**: Testes unitários chamando cada método do `GlobalExceptionHandler` diretamente com uma exceção construída, verificando `HttpStatus` e corpo da resposta.

---

### P1: Geração e validação de JWT ⭐ MVP

**User Story**: Como sistema de autenticação, preciso gerar tokens JWT distintos para host (autenticado) e guest (anônimo), com claims e expirações diferentes, e validar tokens recebidos.

**Acceptance Criteria**:

1. WHEN `generateHostToken(hostId, email)` é chamado THEN o token gerado SHALL ter `subject=hostId`, claim `role="HOST"`, claim `email=email`
2. WHEN `generateGuestToken(deviceId, displayName, eventId)` é chamado THEN o token gerado SHALL ter `subject=deviceId`, claim `role="GUEST"`, claim `displayName=displayName`, claim `eventId=eventId`
3. WHEN um token válido (recém-gerado) é passado para `isValid(token)` THEN o sistema SHALL retornar `true`
4. WHEN um token malformado/adulterado é passado para `isValid(token)` THEN o sistema SHALL retornar `false` (sem lançar exceção)
5. WHEN um token expirado é passado para `isValid(token)` THEN o sistema SHALL retornar `false`
6. WHEN `extractSubject`, `extractRole`, `extractEventId` são chamados sobre um token válido THEN cada um SHALL retornar exatamente o claim correspondente usado na geração
7. WHEN um host token e um guest token são gerados no mesmo instante THEN suas datas de expiração SHALL diferir conforme `eterniza.jwt.expiration-ms` (host) vs `eterniza.jwt.guest-expiration-ms` (guest), já configurados em `application.yml`

**Independent Test**: Testes unitários instanciando `JwtUtil` com segredo/expiração de teste, gerando tokens e verificando claims/validade via `extractClaims`.

---

## Edge Cases

- WHEN `NotFoundException` recebe um `id` não-String (ex.: `UUID`) THEN a mensagem SHALL usar `String.valueOf`/`toString()` implícito do `formatted()` (comportamento padrão do Java, sem tratamento especial)
- WHEN um token JWT expira exatamente no limite THEN `isValid` SHALL retornar `false` (biblioteca `jjwt` trata expiração como exclusiva — comportamento padrão da lib, não testado explicitamente por depender de timing exato)

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| CFG-04 | P1: Envelope de resposta | Implementing | Pending |
| CFG-05 | P1: Exceções mapeadas | Implementing | Pending |
| CFG-06 | P1: JWT geração/validação | Implementing | Pending |

**Coverage:** 3 total, 3 mapeados para execução, 0 sem mapeamento

---

## Success Criteria

- [ ] `mvn -o compile` e `mvn -o test` passam sem falhas
- [ ] Aplicação sobe sem erros relacionados a `common` (critério original do BACKEND_SPEC §3.5)
