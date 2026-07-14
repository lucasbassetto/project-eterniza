# Eterniza — Contrato da API (para o app/frontend)

> **Este é o documento de referência para implementar o aplicativo.** Ele descreve
> apenas o contrato HTTP — não o código interno do backend. Você **não precisa** do
> código-fonte do backend para construir o app.
>
> O `BACKEND_SPEC.md` é um tutorial de construção do backend e está **defasado**;
> não use como referência de API.

---

## 1. O que é o Eterniza

Um app de fotos para eventos (casamentos, festas). O fluxo:

1. O **host** (dono do evento) cria uma conta e um evento, definindo uma data de **revelação** (`revealAt`).
2. O evento gera um **slug** (link público / QR code) que o host compartilha com os convidados.
3. O **convidado** entra pelo link, cria uma sessão anônima (só nome + device) e tira fotos.
4. As fotos ficam **escondidas** até a data de revelação — os convidados veem só a *contagem*, não as imagens.
5. Quando chega a `revealAt`, o evento é revelado automaticamente e a galeria abre para todos.

**Metáfora**: é como uma câmera descartável de casamento — você tira as fotos, mas só vê o resultado quando o filme é revelado.

---

## 2. Base e formato de resposta

**Base URL**: `http://localhost:8080` (dev)

**Toda** resposta da API vem neste envelope:

```json
{
  "success": true,
  "message": "Evento criado",
  "data": { },
  "timestamp": "2026-07-14T02:30:00Z"
}
```

- `success`: `true` em sucesso, `false` em erro
- `message`: opcional — **omitido do JSON quando nulo**. Em erros, contém a mensagem.
- `data`: o payload (objeto, array, string, ou ausente em erros)
- `timestamp`: instante da resposta (ISO-8601 UTC)

**A documentação OpenAPI é gerada automaticamente** e é a fonte de verdade machine-readable:
- `GET /api-docs` → especificação OpenAPI (JSON)
- `GET /swagger-ui.html` → interface interativa

---

## 3. Formato de erro

Erros usam o mesmo envelope, com `success: false` e a mensagem em `message`:

```json
{
  "success": false,
  "message": "Credenciais inválidas",
  "timestamp": "2026-07-14T02:30:00Z"
}
```

| Status | Quando acontece | Exemplo de `message` |
|---|---|---|
| **400** | Regra de negócio violada | `"E-mail já cadastrado"`, `"Arquivo vazio"` |
| **400** | Validação de payload falhou | mensagens dos campos **unidas por `, `** (ver abaixo) |
| **401** | Credenciais inválidas, ou rota protegida sem token / com token inválido | `"Credenciais inválidas"` |
| **403** | Autenticado, mas sem permissão sobre o recurso | `"Você não é o dono deste evento"` |
| **404** | Recurso não encontrado | `"Evento não encontrado: {slug}"` |
| **500** | Erro inesperado | `"Erro interno. Tente novamente."` |

⚠️ **Validação**: quando vários campos falham, as mensagens vêm **concatenadas numa única string** separadas por vírgula. Ex.:

```json
{
  "success": false,
  "message": "Nome é obrigatório, Senha deve ter no mínimo 8 caracteres"
}
```

Não há um objeto de erros por campo — o app precisa lidar com essa string única (ou o backend precisaria ser alterado para devolver erros estruturados por campo; ver §9).

⚠️ **401 sem corpo**: quando o filtro de segurança bloqueia (rota protegida sem header `Authorization`, ou token inválido), a resposta é **401 com corpo vazio** — sem o envelope JSON. O app deve tratar 401 pelo status, não pelo body.

---

## 4. Autenticação

Existem **dois tipos de token JWT**, com propósitos diferentes:

| | **hostToken** | **guestToken** |
|---|---|---|
| Quem | Dono do evento (tem conta) | Convidado (anônimo) |
| Obtido em | `POST /api/auth/register` ou `/login` | `POST /api/auth/guest/session` |
| Validade | 24h (padrão) | 7 dias (padrão) |
| `sub` (subject) | `hostId` (UUID) | `deviceId` (string livre) |
| Outros claims | `email`, `role: "HOST"` | `displayName`, `eventId`, `role: "GUEST"` |
| Usado em | criar evento, listar meus eventos | upload de foto |

**Como enviar** (em toda rota protegida):

```
Authorization: Bearer <token>
```

### Rotas públicas (sem token)
- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/guest/session`
- `GET /api/events/slug/{slug}`
- `GET /api/photos/gallery/{eventId}`
- `GET /api-docs`, `GET /swagger-ui.html`

### Rotas protegidas
| Rota | Token |
|---|---|
| `POST /api/events` | hostToken |
| `GET /api/events/my` | hostToken |
| `POST /api/events/{id}/reveal` | hostToken (**só o dono**) |
| `POST /api/photos/upload` | guestToken |

---

## 5. Endpoints

### 5.1 `POST /api/auth/register` — criar conta de host

Público. Body JSON:

```json
{
  "name": "Lucas Host",
  "email": "lucas@eterniza.com",
  "password": "senha1234"
}
```

Validação:
- `name`: obrigatório → `"Nome é obrigatório"`
- `email`: obrigatório e formato válido → `"E-mail é obrigatório"`
- `password`: mínimo **8 caracteres** → `"Senha deve ter no mínimo 8 caracteres"`

**201 Created**:
```json
{
  "success": true,
  "message": "Conta criada com sucesso",
  "data": { "token": "eyJ...", "name": "Lucas Host", "email": "lucas@eterniza.com" }
}
```

Erros: **400** `"E-mail já cadastrado"`.

---

### 5.2 `POST /api/auth/login` — login do host

Público. Body JSON:

```json
{ "email": "lucas@eterniza.com", "password": "senha1234" }
```

**200 OK** — mesmo `data` do register (`token`, `name`, `email`).

Erros: **401** `"Credenciais inválidas"` (mesma mensagem para e-mail inexistente **e** senha errada — proposital, não vaza se o e-mail existe).

---

### 5.3 `POST /api/auth/guest/session` — sessão anônima do convidado

Público. Body JSON:

```json
{
  "displayName": "Ana",
  "eventId": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "deviceId": "device-abc-123"
}
```

Validação:
- `displayName`: obrigatório, **máx. 30 caracteres**
- `eventId`: obrigatório (o **id** do evento, não o slug)
- `deviceId`: obrigatório — identificador do dispositivo, gerado e persistido **pelo app** (ex.: UUID salvo no storage local). Serve para associar as fotos ao convidado.

**200 OK** — ⚠️ atenção: `data` é a **string do token diretamente**, não um objeto:
```json
{ "success": true, "data": "eyJhbGciOiJIUzI1NiJ9..." }
```

⚠️ **Este endpoint não valida se o evento existe** e não consome vaga de convidado (ver §9).

---

### 5.4 `POST /api/events` — criar evento

🔒 hostToken. Body JSON:

```json
{
  "name": "Casamento Ana & João",
  "revealAt": "2026-08-01T20:00:00Z",
  "guestLimit": 10
}
```

Validação:
- `name`: obrigatório → `"Nome do evento é obrigatório"`
- `revealAt`: obrigatório e **no futuro** (ISO-8601 UTC) → `"Revelação deve ser no futuro"`
- `guestLimit`: **opcional** (padrão **5** se omitido)

**201 Created**, `message: "Evento criado"`, `data` = **EventResponse** (§6.1).

---

### 5.5 `GET /api/events/slug/{slug}` — buscar evento pelo slug (QR code)

Público. É o endpoint que o app chama quando o convidado abre o link/QR do evento.

**200 OK** → `data` = EventResponse.
Erros: **404** `"Evento não encontrado: {slug}"`.

---

### 5.6 `GET /api/events/my` — listar eventos do host

🔒 hostToken. Sem body.

**200 OK** → `data` = **array** de EventResponse, ordenado por criação (mais recente primeiro). Array vazio se não houver eventos.

---

### 5.7 `POST /api/events/{id}/reveal` — revelar o evento agora

🔒 hostToken. Sem body. Revela o evento **imediatamente**, antes da `revealAt` — abre a galeria e dispara o e-mail ao host.

Só o **dono** do evento pode revelar.

**200 OK**, `message: "Evento revelado"`, `data` = EventResponse (com `status: "REVEALED"`).

| Status | Quando |
|---|---|
| 200 | Revelado com sucesso — **idempotente**: revelar um evento já revelado devolve 200 e não reenvia o e-mail |
| 401 | Sem token / token inválido |
| **403** | Autenticado, mas **não é o dono** do evento → `"Você não é o dono deste evento"` |
| 404 | Evento não existe |

---

### 5.8 `POST /api/photos/upload` — enviar foto

🔒 guestToken. **`multipart/form-data`** (não JSON):

| Campo | Tipo | Descrição |
|---|---|---|
| `file` | binário | A imagem **já com o filtro aplicado** (ver §8) |
| `eventId` | texto | UUID do evento |

Regras do arquivo:
- Tipos aceitos: **`image/jpeg`, `image/png`, `image/webp`**
- Tamanho máximo: **20 MB**

O `deviceId` e o `displayName` do convidado **não vão no body** — o backend os extrai do próprio guestToken.

**201 Created**:
```json
{
  "success": true,
  "data": { "photoId": "b4f9...", "message": "Foto recebida!" }
}
```

Erros:
| Status | Mensagem |
|---|---|
| 400 | `"Arquivo vazio"` |
| 400 | `"Formato inválido. Envie JPEG, PNG ou WebP"` |
| 400 | `"Arquivo muito grande. Máximo 20MB"` |
| 401 | (sem token / token inválido — corpo vazio) |

A foto é armazenada e fica **imediatamente pronta**. Não há processamento assíncrono: não existe estado "processando", nem polling, nem webhook. Se retornou 201, acabou.

---

### 5.9 `GET /api/photos/gallery/{eventId}` — galeria do evento

Público (sem token). É o coração do modelo de "revelação".

**200 OK** → `data` = **GalleryResponse** (§6.2):

**Antes da revelação** (evento `ACTIVE`):
```json
{ "revealed": false, "totalPhotos": 12, "photoUrls": [] }
```
→ O app mostra "12 fotos já capturadas" mas **nenhuma imagem**.

**Depois da revelação** (evento `REVEALED`):
```json
{
  "revealed": true,
  "totalPhotos": 12,
  "photoUrls": [
    "https://<bucket-publico>.r2.dev/events/{eventId}/originals/{photoId}.jpg",
    "..."
  ]
}
```

✅ `photoUrls` são **URLs públicas completas** — o app consome direto (`<img src="...">`), sem precisar conhecer o bucket nem montar nada.

---

## 6. Modelos

### 6.1 EventResponse

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "Casamento Ana & João",
  "slug": "ecfa1dfe-3177-46a9-bdd1-0d835676febe",
  "qrCodeUrl": "http://localhost:3000/e/ecfa1dfe-3177-46a9-bdd1-0d835676febe",
  "status": "ACTIVE",
  "revealAt": "2026-08-01T20:00:00Z",
  "guestLimit": 10,
  "guestCount": 0,
  "photoCount": 0,
  "createdAt": "2026-07-14T02:30:00Z"
}
```

| Campo | Observação |
|---|---|
| `slug` | Atualmente é um **UUID** (não um slug legível). É o identificador público do evento. |
| `qrCodeUrl` | ⚠️ **Não é uma imagem de QR code** — é o **link público** do evento (`{webUrl}/e/{slug}`). O app é quem deve gerar a imagem do QR a partir dessa string. |
| `status` | `ACTIVE` (fotos escondidas) ou `REVEALED` (galeria aberta) |
| `revealAt` | Quando o evento será revelado automaticamente |
| `guestCount` | ⚠️ **Sempre 0** — nunca é incrementado (ver §9) |
| `photoCount` | ✅ Contagem **real** de fotos do evento (calculada da tabela de fotos). Útil para o host ver quantas fotos já foram tiradas sem chamar a galeria. |

### 6.2 GalleryResponse

```json
{ "revealed": false, "totalPhotos": 12, "photoUrls": [] }
```

- `revealed`: se o evento já foi revelado
- `totalPhotos`: contagem real de fotos (**esta é confiável**)
- `photoUrls`: chaves de storage; **vazio** enquanto não revelado

### 6.3 AuthResponse

```json
{ "token": "eyJ...", "name": "Lucas Host", "email": "lucas@eterniza.com" }
```

---

## 7. Fluxos end-to-end

### Fluxo do host
```
1. POST /api/auth/register  (ou /login)     → guarda hostToken
2. POST /api/events                          → recebe eventId, slug, qrCodeUrl
3. Gera a imagem do QR code a partir de qrCodeUrl e compartilha
4. GET /api/events/my                        → acompanha seus eventos
5. GET /api/photos/gallery/{eventId}         → vê a contagem subindo (fotos escondidas)
6. Na revealAt → recebe e-mail e a galeria abre
   (ou POST /api/events/{id}/reveal → revela na hora, sem esperar)
```

### Fluxo do convidado
```
1. Abre o link/QR → o app extrai o slug
2. GET /api/events/slug/{slug}               → obtém o evento (e o eventId)
3. Gera/recupera um deviceId local (persistente)
4. POST /api/auth/guest/session               → guarda guestToken
5. Câmera: escolhe filtro AO VIVO, tira a foto  (§8)
6. POST /api/photos/upload  (imagem já filtrada) → 201
7. GET /api/photos/gallery/{eventId}          → antes: só contagem; depois: as fotos
```

### Revelação

Duas formas, ambas levam o evento a `REVEALED` e disparam o e-mail ao host:

1. **Automática (por tempo)** — um scheduler roda **a cada 60 segundos** e revela os eventos cuja `revealAt` já passou.
2. **Manual (pelo host)** — `POST /api/events/{id}/reveal` (§5.7). O app pode oferecer um botão "revelar agora" ao dono do evento.

Revelar é **idempotente**: revelar de novo não reenvia o e-mail.

ℹ️ A notificação por e-mail é **fire-and-forget**: se o broker de mensagens estiver fora, o evento **é revelado mesmo assim** (a galeria abre) e apenas o e-mail é perdido. O app nunca vai receber erro por causa disso.

---

## 8. Responsabilidade do app: os filtros são CLIENT-SIDE

**Isto é crítico e não é óbvio pela API.**

O backend **não aplica nenhum filtro**. O modelo é o do Instagram/Snapchat:

1. O app mostra os filtros **ao vivo na câmera** — o convidado vê o efeito em tempo real e escolhe qual quer (ou nenhum).
2. O app aplica o filtro na imagem ("queima" o efeito) **no dispositivo**.
3. O app envia ao `POST /api/photos/upload` a **imagem final, já filtrada**.
4. O servidor apenas armazena. Não há processamento, nem estado "processando".

**Consequências para o app:**
- Implementar os filtros é 100% trabalho do frontend (shaders/GPU na câmera).
- O backend **não sabe** qual filtro foi usado — não há campo para isso. Se quiser registrar/exibir o filtro usado por foto, o backend precisa de um campo novo.
- A imagem original **sem filtro nunca chega ao servidor**. Se o host precisar da versão limpa um dia, isso exigiria mudança no backend (subir as duas versões).

---

## 9. Limitações conhecidas do backend (leia antes de projetar o app)

Encontradas ao ler o código. Nenhuma delas impede o app de funcionar, mas mudam o que dá pra prometer no produto:

| # | Limitação | Impacto no app |
|---|---|---|
| ~~1~~ | ~~`photoUrls` retorna chaves, não URLs~~ | ✅ **CORRIGIDO** — a galeria já devolve URLs públicas completas. |
| 2 | **`guestCount` nunca incrementa e `guestLimit` nunca é aplicado** | O limite de convidados **não é enforced**. Qualquer número de convidados entra. |
| 3 | **A sessão de guest não valida se o evento existe** | É possível gerar um guestToken para um `eventId` inexistente. O app deve validar antes via `GET /api/events/slug/{slug}`. |
| ~~4~~ | ~~`photoCount` no EventResponse é sempre 0~~ | ✅ **CORRIGIDO** — agora reflete a contagem real de fotos. |
| ~~5~~ | ~~Não há endpoint de "revelar agora"~~ | ✅ **CORRIGIDO** — `POST /api/events/{id}/reveal` (só o dono). |
| 6 | **Não há endpoint para listar/apagar uma foto individual** | Só a galeria agregada. Sem "apagar minha foto". |
| 7 | **Erros de validação vêm como string única concatenada** | Não dá para destacar o campo com erro no formulário sem fazer parsing da string. |
| 8 | **`slug` é um UUID**, não um slug legível | O link do QR fica feio (`/e/ecfa1dfe-3177-...`). |

Se alguma delas for importante para o produto, o backend precisa ser ajustado — vale alinhar **antes** de o app ser construído em cima do comportamento atual.

---

## 10. Configuração relevante

| Config | Onde aparece no contrato |
|---|---|
| `eterniza.app.web-url` | Base do `qrCodeUrl` (ex.: `http://localhost:3000`) — é a URL do **frontend**, não da API |
| `eterniza.r2.public-url` | Base pública do bucket, necessária para montar a URL das fotos (§5.8) |
| `eterniza.jwt.expiration-ms` | Validade do hostToken (padrão 24h) |
| `eterniza.jwt.guest-expiration-ms` | Validade do guestToken (padrão 7 dias) |
