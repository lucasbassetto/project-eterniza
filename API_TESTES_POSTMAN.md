# Guia de Testes da API Eterniza — Passo a Passo

Base URL: `http://localhost:8080`

## Sobre o token

Existem **dois tipos de token JWT**, e cada rota protegida exige um tipo específico:

| Token | De onde vem | Usado em |
|---|---|---|
| **hostToken** | Resposta do `/api/auth/register` ou `/api/auth/login` | Rotas de host: criar evento, listar meus eventos |
| **guestToken** | Resposta do `/api/auth/guest/session` | Rota de guest: upload de foto |

O token vai sempre no **header** da requisição, neste formato exato:

```
Authorization: Bearer <token>
```

(Sem esquecer a palavra `Bearer` seguida de um espaço, antes do token.)

Rotas **públicas** (não precisam de header `Authorization`): register, login, guest/session, buscar evento por slug, galeria de fotos.

---

## Passo 1 — Registrar host

**POST** `/api/auth/register`

Body → aba **Body → raw → JSON**:
```json
{
  "name": "Lucas Host",
  "email": "lucas.teste@eterniza.com",
  "password": "senha1234"
}
```

Sem header de autenticação.

Resposta esperada (201):
```json
{
  "success": true,
  "message": "Conta criada com sucesso",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "name": "Lucas Host",
    "email": "lucas.teste@eterniza.com"
  }
}
```

📌 **Copie `data.token`** — esse é o seu **hostToken**. Vai ser usado nos passos 3 e 5.

⚠️ Se rodar de novo com o mesmo e-mail, vai dar 400 "E-mail já cadastrado". Troque o e-mail ou pule para o Passo 2 (login).

---

## Passo 2 — Login do host (alternativa ao registro)

**POST** `/api/auth/login`

Body:
```json
{
  "email": "lucas.teste@eterniza.com",
  "password": "senha1234"
}
```

Sem header de autenticação. Mesmo shape de resposta do registro — copie `data.token` (hostToken).

---

## Passo 3 — Criar evento

**POST** `/api/events`

Headers:
| Key | Value |
|---|---|
| `Authorization` | `Bearer <cole o hostToken aqui>` |

Body:
```json
{
  "name": "Casamento Ana & João",
  "revealAt": "2026-07-21T12:00:00Z"
}
```

Regras do body:
- `revealAt`: **precisa ser uma data no futuro** (formato ISO 8601 com `Z`). Ajuste para depois da data de hoje.

Sem o header `Authorization` → 401.

Resposta esperada (201):
```json
{
  "success": true,
  "message": "Evento criado",
  "data": {
    "id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
    "name": "Casamento Ana & João",
    "slug": "casamento-ana-joao-x7k2",
    "qrCodeUrl": "...",
    "status": "ACTIVE",
    "revealAt": "2026-07-21T12:00:00Z",
    "photoCount": 0,
    "createdAt": "..."
  }
}
```

📌 **Copie `data.id`** (esse é o **eventId**) **e `data.slug`** — usados nos passos seguintes.

---

## Passo 4 — Buscar evento pelo slug (público)

**GET** `/api/events/slug/{slug}`

Exemplo: `/api/events/slug/casamento-ana-joao-x7k2`

Substitua `{slug}` na URL pelo slug do Passo 3. Sem body, sem header.

Resposta (200): mesmo shape do EventResponse acima. Slug inexistente → 404.

---

## Passo 5 — Listar meus eventos

**GET** `/api/events/my`

Headers:
| Key | Value |
|---|---|
| `Authorization` | `Bearer <hostToken do Passo 1 ou 2>` |

Sem body. Resposta (200): `data` é um array de EventResponse — deve conter o evento criado no Passo 3.

Sem o header → 401.

---

## Passo 6 — Criar sessão de guest

**POST** `/api/auth/guest/session`

Body:
```json
{
  "displayName": "Ana Convidada",
  "eventId": "<cole o eventId do Passo 3>",
  "deviceId": "device-postman-001"
}
```

Sem header de autenticação.

Regras do body:
- `displayName`: obrigatório, máximo 30 caracteres
- `eventId`: obrigatório (é o id do evento, não o slug)
- `deviceId`: obrigatório — identifica o "dispositivo" do convidado (pode ser qualquer string fixa sua para os testes)

Resposta esperada (200):
```json
{
  "success": true,
  "data": "eyJhbGciOiJIUzI1NiJ9..."
}
```

📌 Aqui `data` é a própria string do token — **copie isso**, é o seu **guestToken**. Usado no Passo 7.

---

## Passo 7 — Upload de foto

**POST** `/api/photos/upload`

⚠️ **Este não é um body JSON.** No Postman, vá na aba **Body → form-data** (não "raw").

Headers:
| Key | Value |
|---|---|
| `Authorization` | `Bearer <guestToken do Passo 6>` |

Body (form-data):
| Key | Tipo (no Postman) | Value |
|---|---|---|
| `file` | **File** (mude o dropdown de "Text" para "File") | selecione uma imagem `.jpg`, `.png` ou `.webp` do seu computador |
| `eventId` | Text | `<eventId do Passo 3>` |

Regras:
- Arquivo vazio → 400 "Arquivo vazio"
- Tipo diferente de JPEG/PNG/WebP → 400 "Formato inválido. Envie JPEG, PNG ou WebP"
- Arquivo maior que 20MB → 400 "Arquivo muito grande. Máximo 20MB"
- Sem header `Authorization` → 401

Resposta esperada (201):
```json
{
  "success": true,
  "data": {
    "photoId": "b4f9...",
    "message": "Foto recebida!"
  }
}
```

O filtro é aplicado **ao vivo no aplicativo** (client-side, estilo Instagram) antes do envio — o convidado escolhe o filtro que quiser na câmera. O servidor recebe a imagem já finalizada, apenas armazena e grava a foto com `status = READY` na hora. Não há processamento de imagem no servidor — por isso a foto já nasce pronta e **nunca muda de status depois** (o retorno é `201 Created`, não `202 Accepted`, já que não há nada sendo processado em background).

---

## Passo 8 — Ver galeria do evento (público)

**GET** `/api/photos/gallery/{eventId}`

Exemplo: `/api/photos/gallery/3fa85f64-5717-4562-b3fc-2c963f66afa6`

Substitua `{eventId}` na URL pelo eventId do Passo 3. Sem body, sem header.

Resposta (200):
```json
{
  "success": true,
  "data": {
    "revealed": false,
    "totalPhotos": 1,
    "photoUrls": []
  }
}
```

- Enquanto o evento **não foi revelado** (`status = ACTIVE`): `revealed = false` e `photoUrls` sempre vazio, mesmo com fotos prontas — só a contagem em `totalPhotos` aparece.
- Depois de revelado (`status = REVEALED`): `revealed = true` e `photoUrls` traz as URLs das fotos com `status = READY`.

---

## Resumo visual do fluxo de tokens

```
Passo 1/2 (Register/Login) ──► hostToken ──┬──► Passo 3 (Create event)
                                            └──► Passo 5 (My events)

Passo 3 (Create event) ──► eventId ──┬──► Passo 4 (Get by slug, via slug)
                                      ├──► Passo 6 (Guest session)
                                      ├──► Passo 7 (Upload, via eventId)
                                      └──► Passo 8 (Gallery)

Passo 6 (Guest session) ──► guestToken ──► Passo 7 (Upload photo)
```

## Rotas que NÃO precisam de token

- `POST /api/auth/register`
- `POST /api/auth/login`
- `POST /api/auth/guest/session`
- `GET /api/events/slug/{slug}`
- `GET /api/photos/gallery/{eventId}`
- `GET /api-docs`
- `GET /swagger-ui.html`

## Rotas que PRECISAM de token

| Rota | Token necessário |
|---|---|
| `POST /api/events` | hostToken |
| `GET /api/events/my` | hostToken |
| `POST /api/photos/upload` | guestToken |
