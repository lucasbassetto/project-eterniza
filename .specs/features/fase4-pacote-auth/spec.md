# Fase 4 — Pacote auth Specification

## Problem Statement

O backend ainda não tem como autenticar hosts nem emitir sessões anônimas para guests. Sem isso, nenhuma rota de evento/foto pode ser protegida e nenhum host consegue criar conta. A Fase 3 (common: ApiResponse, exceptions, JwtUtil) está pronta e validada — esta fase consome essa infraestrutura para implementar o fluxo real de autenticação.

## Goals

- [ ] Host consegue se registrar e receber um token JWT válido
- [ ] Host consegue fazer login com credenciais corretas e receber um token JWT válido
- [ ] Guest consegue obter um token JWT anônimo (sem cadastro) associado a um evento e device
- [ ] Rotas não-públicas exigem um Bearer token válido (401 caso contrário); rotas públicas listadas ficam acessíveis sem token

## Out of Scope

Explicitamente excluído desta fase. Documentado para evitar scope creep.

| Feature | Reason |
| --- | --- |
| Refresh tokens / logout / blacklist de tokens | Não mencionado no BACKEND_SPEC.md Fase 4; JWT é stateless com expiração configurada |
| Autorização por role no SecurityContext (ex. `@PreAuthorize`) | O próprio BACKEND_SPEC.md deixa o `SecurityContext` vazio no `JwtAuthFilter`, com comentário explícito de que é possível popular "no futuro" — fora do escopo atual |
| Rate limiting em `/register` e `/login` | Não especificado no BACKEND_SPEC.md; seria uma preocupação de infraestrutura separada |
| Recuperação de senha / verificação de e-mail | Não mencionado na Fase 4 |
| Endpoints de evento/foto reais nas rotas públicas (`/api/events/slug/`, `/api/photos/gallery/`) | Serão implementados nas Fases 5 e 6; aqui apenas o prefixo é registrado como público no `SecurityConfig` |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| `JwtAuthFilter` não popula o `SecurityContext` | Segue exatamente o código do BACKEND_SPEC.md (apenas valida o token e retorna 401 se inválido) | O próprio spec documenta isso como decisão futura, não desta fase | y (fonte: BACKEND_SPEC.md linhas 803-809) |
| Corrida de cadastro duplicado (dois `register` concorrentes com mesmo e-mail) | Confiar na constraint `UNIQUE` de `hosts.email` (V1 migration) como rede de segurança além do `existsByEmail` em nível de aplicação; a segunda requisição falha com erro de integridade, tratado pelo `handleGeneric` (500) | `existsByEmail` sozinho tem TOCTOU; a spec não pede tratamento dedicado (ex. converter para 400), então o comportamento genérico do `GlobalExceptionHandler` já é suficiente | y — assumido, não há requisito explícito no BACKEND_SPEC.md |
| Sessão guest não é idempotente (cada chamada gera um novo token) | Comportamento esperado — sessões são stateless, reemitir é intencional. **Corrigido**: unicidade do token NÃO é garantida entre chamadas no mesmo segundo (sem `jti`, `iat`/`exp` com granularidade de segundo) | JWT stateless não tem conceito de "sessão existente" para deduplicar; unicidade byte-a-byte não é um requisito do BACKEND_SPEC.md e não é garantida pelo `JwtUtil` validado na Fase 3 | y — corrigido após falha de teste em T4 (AuthServiceTest), decisão do usuário: remover a AC de unicidade em vez de adicionar `jti` ao JwtUtil |
| Rate limiting / observability adicional em auth | N/A nesta fase | Não mencionado no BACKEND_SPEC.md; `GlobalExceptionHandler` já loga erros inesperados (Fase 3) | y |

**Open questions:** none — todas resolvidas ou registradas acima.

---

## User Stories

### P1: Registro de host ⭐ MVP

**User Story**: Como host, quero criar uma conta com nome, e-mail e senha, para poder gerenciar meus próprios eventos.

**Why P1**: Sem cadastro não há host algum no sistema — bloqueia todo o resto do fluxo autenticado.

**Acceptance Criteria**:

1. WHEN um POST `/api/auth/register` é enviado com nome, e-mail válido e senha ≥ 8 caracteres, e o e-mail ainda não existe, THEN o sistema SHALL persistir o host com a senha codificada via BCrypt e retornar 201 com `AuthResponse` contendo um JWT válido, o nome e o e-mail do host.
2. WHEN o e-mail informado já está cadastrado THEN o sistema SHALL retornar 400 com mensagem "E-mail já cadastrado" e SHALL NOT persistir um novo registro.
3. WHEN o nome está em branco, OR o e-mail não é um e-mail válido, OR a senha tem menos de 8 caracteres THEN o sistema SHALL retornar 400 com as mensagens de validação correspondentes, unidas por ", ".
4. WHEN o registro é bem-sucedido THEN o token retornado SHALL ter `role=HOST`, `subject` igual ao UUID do host recém-criado, e `email` igual ao e-mail cadastrado (via claims do `JwtUtil.generateHostToken`).

**Independent Test**: Chamar `POST /api/auth/register` com payload válido, receber 201 e token; decodificar o token e conferir subject/claims; repetir com o mesmo e-mail e receber 400.

---

### P1: Login de host ⭐ MVP

**User Story**: Como host cadastrado, quero fazer login com e-mail e senha, para obter um token válido e acessar minhas rotas protegidas.

**Why P1**: Sem login, um host cadastrado não consegue autenticar em sessões futuras.

**Acceptance Criteria**:

1. WHEN um POST `/api/auth/login` é enviado com e-mail cadastrado e senha correta THEN o sistema SHALL retornar 200 com `AuthResponse` contendo um novo JWT válido, nome e e-mail do host.
2. WHEN o e-mail não está cadastrado THEN o sistema SHALL retornar 401 com mensagem "Credenciais inválidas" (SHALL NOT revelar se o e-mail existe ou não).
3. WHEN o e-mail existe mas a senha está incorreta THEN o sistema SHALL retornar 401 com a mesma mensagem "Credenciais inválidas".
4. WHEN o login é bem-sucedido THEN o token retornado SHALL ter `role=HOST` e `subject` igual ao UUID do host autenticado.

**Independent Test**: Registrar um host, então chamar `POST /api/auth/login` com a senha correta (200 + token) e com senha errada (401); chamar login com e-mail inexistente (401) e confirmar mensagem idêntica ao caso de senha errada.

---

### P1: Sessão anônima de guest ⭐ MVP

**User Story**: Como guest (convidado de um evento), quero obter um token anônimo informando meu nome de exibição, o evento e meu device, para poder enviar fotos sem precisar criar conta.

**Why P1**: O fluxo do guest (upload de fotos) depende de um token válido; sem essa rota, guests não têm como se autenticar.

**Acceptance Criteria**:

1. WHEN um POST `/api/auth/guest/session` é enviado com `displayName` (não vazio, ≤ 30 caracteres), `eventId` (não vazio) e `deviceId` (não vazio) THEN o sistema SHALL retornar 200 com um JWT (`ApiResponse<String>`) cujo `subject` é o `deviceId`, `role=GUEST`, e claims `displayName`/`eventId` conforme enviados.
2. WHEN `displayName` está vazio, OR excede 30 caracteres, OR `eventId`/`deviceId` estão em branco THEN o sistema SHALL retornar 400 com as mensagens de validação correspondentes.
3. WHEN a mesma requisição de sessão guest é enviada duas vezes THEN o sistema SHALL emitir um novo token JWT a cada chamada, sem deduplicação nem estado persistido (comportamento stateless esperado). **Correção de precisão**: como o `JwtUtil` (Fase 3) usa `iat`/`exp` com granularidade de segundo e não possui `jti`, duas chamadas dentro do mesmo segundo podem produzir tokens byte-idênticos — isso é aceito, não é um requisito de unicidade garantida.

**Independent Test**: Chamar `POST /api/auth/guest/session` com payload válido, decodificar o token retornado e conferir subject/claims; enviar payload inválido (nome vazio) e confirmar 400.

---

### P1: Proteção de rotas via JWT ⭐ MVP

**User Story**: Como sistema, quero exigir um Bearer token válido em qualquer rota que não esteja na lista pública, para impedir acesso não-autenticado a recursos protegidos.

**Why P1**: É a peça que efetivamente torna a autenticação útil — sem isso, `JwtUtil` e os tokens emitidos não protegem nada.

**Acceptance Criteria**:

1. WHEN uma requisição chega para uma rota pública (`/api/auth/register`, `/api/auth/login`, `/api/auth/guest/session`, prefixo `/api/events/slug/`, prefixo `/api/photos/gallery/`, `/swagger-ui`, `/swagger-ui.html`, `/api-docs`) sem header `Authorization` THEN o sistema SHALL permitir a requisição (não retornar 401 pelo filtro).
2. WHEN uma requisição chega para qualquer outra rota sem header `Authorization`, OR com header que não começa com "Bearer ", THEN o sistema SHALL retornar 401 e SHALL NOT repassar a requisição adiante na cadeia de filtros.
3. WHEN uma requisição chega para uma rota não-pública com header `Authorization: Bearer <token>` onde `<token>` é estruturalmente inválido (assinatura incorreta ou expirado) THEN o sistema SHALL retornar 401 e SHALL NOT repassar a requisição adiante.
4. WHEN uma requisição chega para uma rota não-pública com um `<token>` válido (assinatura correta e não expirado) THEN o sistema SHALL repassar a requisição adiante na cadeia de filtros (chain.doFilter chamado).
5. WHEN a aplicação sobe THEN CSRF SHALL estar desabilitado e a política de sessão SHALL ser `STATELESS` (sem `HttpSession` criada).

**Independent Test**: Com `MockMvc`/filtro isolado, simular requisição sem token para rota protegida (401), com token válido para rota protegida (passa adiante), sem token para rota pública (passa adiante).

---

## Edge Cases

- WHEN dois `POST /api/auth/register` concorrentes usam o mesmo e-mail (corrida além do `existsByEmail`) THEN a constraint `UNIQUE(email)` do banco SHALL impedir o segundo insert, e o erro de integridade SHALL cair no `handleGeneric` (500) — ver Assumptions.
- WHEN o header `Authorization` está presente mas vazio (`Authorization: `) THEN o filtro SHALL tratar como ausência de Bearer token válido e permitir seguir apenas se a rota for pública, senão 401.
- WHEN o `eventId` enviado na sessão guest não corresponde a um evento existente THEN esta fase SHALL NOT validar a existência do evento (validação de negócio fica para a Fase 5); o token é emitido normalmente.

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| AUTH-01 | P1: Registro de host | Tasks (T4, T6) | In Tasks |
| AUTH-02 | P1: Registro de host | Tasks (T4, T6) | In Tasks |
| AUTH-03 | P1: Registro de host | Tasks (T6) | In Tasks |
| AUTH-04 | P1: Registro de host | Tasks (T4) | In Tasks |
| AUTH-05 | P1: Login de host | Tasks (T4, T6) | In Tasks |
| AUTH-06 | P1: Login de host | Tasks (T4, T6) | In Tasks |
| AUTH-07 | P1: Login de host | Tasks (T4, T6) | In Tasks |
| AUTH-08 | P1: Login de host | Tasks (T4) | In Tasks |
| AUTH-09 | P1: Sessão anônima de guest | Tasks (T4, T6) | In Tasks |
| AUTH-10 | P1: Sessão anônima de guest | Tasks (T6) | In Tasks |
| AUTH-11 | P1: Sessão anônima de guest | Tasks (T4) | In Tasks |
| AUTH-12 | P1: Proteção de rotas via JWT | Tasks (T6) | In Tasks |
| AUTH-13 | P1: Proteção de rotas via JWT | Tasks (T5, T6) | In Tasks |
| AUTH-14 | P1: Proteção de rotas via JWT | Tasks (T5) | In Tasks |
| AUTH-15 | P1: Proteção de rotas via JWT | Tasks (T6) | In Tasks |
| AUTH-16 | P1: Proteção de rotas via JWT | Tasks (T6) | In Tasks |

**Coverage:** 16 total, 16 mapped to tasks, 0 unmapped ✅

---

## Success Criteria

- [ ] `POST /api/auth/register`, `/login`, `/guest/session` funcionam conforme testado manualmente na seção 4.7 do BACKEND_SPEC.md (via Swagger)
- [ ] Suite de testes automatizados cobre as 16 ACs acima e passa 100%
- [ ] Aplicação sobe sem erros relacionados a `auth` ou `SecurityConfig`
- [ ] Rotas protegidas retornam 401 sem token válido; rotas públicas continuam acessíveis
