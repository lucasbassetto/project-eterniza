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

✅ **Validação de payload** (400): além do `message` concatenado (compatibilidade), a resposta traz **`errors`** — um objeto `campo → mensagem` para o app destacar o campo certo no formulário:

```json
{
  "success": false,
  "message": "Nome é obrigatório, Senha deve ter no mínimo 8 caracteres",
  "errors": {
    "name": "Nome é obrigatório",
    "password": "Senha deve ter no mínimo 8 caracteres"
  }
}
```

- `errors` **só aparece** em erro de validação de payload (nunca em regra de negócio, 404 etc.).
- Se o mesmo campo tem várias violações, `errors` traz a **primeira**; `message` lista todas.

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
| `GET /api/photos/event/{eventId}` | hostToken (**só o dono**) |
| `DELETE /api/photos/{photoId}` | hostToken (**só o dono do evento**) |

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
  "photoLimitPerGuest": 36
}
```

Validação:
- `name`: obrigatório → `"Nome do evento é obrigatório"`
- `revealAt`: obrigatório e **no futuro** (ISO-8601 UTC) → `"Revelação deve ser no futuro"`
- `photoLimitPerGuest`: **opcional** (padrão **10**), entre **1 e 100** → `"Limite de fotos por convidado deve ser no mínimo 1"` / `"... no máximo 100"`

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
  "data": { "photoId": "b4f9...", "message": "Foto recebida!", "photosRemaining": 9 }
}
```

`photosRemaining` = quantas fotos o convidado ainda pode enviar neste evento. Use para atualizar o contador de "poses" no app sem outra chamada.

Erros:
| Status | Mensagem |
|---|---|
| 400 | `"Arquivo vazio"` |
| 400 | `"Formato inválido. Envie JPEG, PNG ou WebP"` |
| 400 | `"Arquivo muito grande. Máximo 20MB"` |
| 400 | `"Você já usou todas as suas {N} fotos neste evento"` — limite de fotos do convidado atingido (`photoLimitPerGuest` do evento; contado por `deviceId`) |
| 401 | (sem token / token inválido — corpo vazio) |
| 404 | `"Evento não encontrado: {eventId}"` — o evento do upload não existe |

O app deve tratar o limite **antes** de abrir a câmera: compare o total já enviado pelo device com `photoLimitPerGuest` do evento e desabilite o botão quando zerar.

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

### 5.10 `GET /api/photos/event/{eventId}` — fotos do evento para moderação

🔒 hostToken, **só o dono do evento**. Lista as fotos (status `READY`) com metadados e URL:

```json
{
  "success": true,
  "data": [
    { "photoId": "b4f9...", "guestName": "Ana", "createdAt": "2026-07-14T21:03:00Z", "url": "https://<bucket>.r2.dev/events/.../b4f9.jpg" }
  ]
}
```

- O host **sempre vê as imagens**, inclusive **antes da revelação** — o bloqueio até o reveal vale para os convidados (galeria pública), não para o dono, que precisa ver o conteúdo para moderar.
- `url` prefere a versão filtrada quando existir (mesma regra da galeria).

Erros: **403** se não for o dono, **404** se o evento não existir.

---

### 5.11 `DELETE /api/photos/{photoId}` — apagar foto (moderação)

🔒 hostToken, **só o dono do evento da foto**. Soft delete:

- A foto **some da galeria** e do `photoCount`, e o arquivo é removido do storage.
- A "pose" do convidado **continua gasta** — apagar não devolve o direito de tirar outra foto (modelo câmera descartável).
- **Idempotente**: apagar uma foto já apagada retorna 200 sem efeito.

**200 OK**, `message: "Foto apagada"`, `data: null`.
Erros: **403** se não for o dono, **404** se a foto não existir.

⚠️ O convidado **não pode** apagar a própria foto — decisão de produto: só o host modera.

---

## 6. Modelos

### 6.1 EventResponse

```json
{
  "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "name": "Casamento Ana & João",
  "slug": "casamento-ana-joao-x7k2",
  "qrCodeUrl": "http://localhost:3000/e/casamento-ana-joao-x7k2",
  "status": "ACTIVE",
  "revealAt": "2026-08-01T20:00:00Z",
  "photoLimitPerGuest": 36,
  "photoCount": 0,
  "createdAt": "2026-07-14T02:30:00Z"
}
```

| Campo | Observação |
|---|---|
| `slug` | Identificador público do evento, **legível**: nome slugificado + sufixo aleatório (`casamento-ana-joao-x7k2`). Eventos criados antes dessa mudança mantêm slug em formato UUID — os dois formatos são válidos. |
| `qrCodeUrl` | ⚠️ **Não é uma imagem de QR code** — é o **link público** do evento (`{webUrl}/e/{slug}`). O app é quem deve gerar a imagem do QR a partir dessa string. |
| `status` | `ACTIVE` (fotos escondidas) ou `REVEALED` (galeria aberta) |
| `revealAt` | Quando o evento será revelado automaticamente |
| `photoLimitPerGuest` | Número de fotos ("poses") que **cada convidado** pode enviar. O app deve mostrar o contador regressivo (ex.: "3 de 10 fotos"). |
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

## 7.1 Salvar a foto no celular (alta resolução)

O backend **não tem endpoint de download** — e não precisa ter.

Depois da revelação, a galeria devolve a **URL pública** de cada foto (§5.9). Para o convidado salvar uma foto no rolo da câmera, o app:

1. Baixa a imagem da URL
2. Grava na galeria do dispositivo com a API nativa (`expo-media-library`, `CameraRoll`, etc.)

A imagem servida é a **original, em resolução total** — o backend não redimensiona nem recomprime nada. Servir direto do bucket (CDN) é mais rápido e não passa pelo servidor.

> ### 🚨 ATENÇÃO — o ponto onde a alta resolução pode morrer
>
> O backend guarda **fielmente os bytes que o app enviar**. Se o app comprimir ou
> redimensionar a imagem **antes do upload**, a foto degradada é o que fica — e o
> original **se perde para sempre**, não há como recuperar.
>
> Quase toda biblioteca de câmera comprime **por padrão** (`quality: 0.7`,
> `maxWidth: 1080`…). **Desative isso.** Capture e envie em resolução máxima.
>
> O limite de 20 MB/foto é folgado para 4K (um JPEG 4K de alta qualidade fica em ~3-6 MB).

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
- **Enviar em resolução máxima, sem compressão** (ver o box de atenção em §7.1) — é o que garante o download em 4K depois.

---

## 9. Limitações conhecidas do backend (leia antes de projetar o app)

Encontradas ao ler o código. Nenhuma delas impede o app de funcionar, mas mudam o que dá pra prometer no produto:

| # | Limitação | Impacto no app |
|---|---|---|
| ~~1~~ | ~~`photoUrls` retorna chaves, não URLs~~ | ✅ **CORRIGIDO** — a galeria já devolve URLs públicas completas. |
| ~~2~~ | ~~`guestCount` nunca incrementa e `guestLimit` nunca é aplicado~~ | ✅ **RESOLVIDO** — o conceito de limite de convidados foi descartado (não é enforceável com `deviceId` forjável) e substituído pelo **limite de fotos por convidado** (`photoLimitPerGuest`), aplicado no upload. |
| 3 | **A sessão de guest não valida se o evento existe** | É possível gerar um guestToken para um `eventId` inexistente (o upload com esse token agora falha com **404**, mas a sessão em si é criada). O app deve validar antes via `GET /api/events/slug/{slug}`. |
| ~~4~~ | ~~`photoCount` no EventResponse é sempre 0~~ | ✅ **CORRIGIDO** — agora reflete a contagem real de fotos. |
| ~~5~~ | ~~Não há endpoint de "revelar agora"~~ | ✅ **CORRIGIDO** — `POST /api/events/{id}/reveal` (só o dono). |
| ~~6~~ | ~~Não há endpoint para listar/apagar uma foto individual~~ | ✅ **RESOLVIDO** — `GET /api/photos/event/{eventId}` (lista para o host) e `DELETE /api/photos/{photoId}` (moderação, soft delete). Por decisão de produto, **só o host** apaga; a pose do convidado não é devolvida. |
| ~~7~~ | ~~Erros de validação vêm como string única concatenada~~ | ✅ **RESOLVIDO** — a resposta 400 de validação agora traz `errors` (objeto campo → mensagem) além do `message` concatenado (ver §3). |
| ~~8~~ | ~~`slug` é um UUID, não um slug legível~~ | ✅ **RESOLVIDO** — slug agora é `nome-slugificado-x7k2`. Eventos antigos mantêm o slug UUID (ambos válidos). |

Se alguma delas for importante para o produto, o backend precisa ser ajustado — vale alinhar **antes** de o app ser construído em cima do comportamento atual.

---

## 10. Configuração relevante

| Config | Onde aparece no contrato |
|---|---|
| `eterniza.app.web-url` | Base do `qrCodeUrl` (ex.: `http://localhost:3000`) — é a URL do **frontend**, não da API |
| `eterniza.r2.public-url` | Base pública do bucket, necessária para montar a URL das fotos (§5.8) |
| `eterniza.jwt.expiration-ms` | Validade do hostToken (padrão 24h) |
| `eterniza.jwt.guest-expiration-ms` | Validade do guestToken (padrão 7 dias) |
