# Eterniza — Especificações do Back-end

> ## ⚠️ DOCUMENTO HISTÓRICO — NÃO USE COMO REFERÊNCIA
>
> Este é o **guia original de construção** do backend (o código Java de cada classe,
> fase por fase). O backend **evoluiu e divergiu** deste documento — várias partes
> descritas aqui **não existem mais**:
>
> - ❌ O pipeline de filtro no servidor (`FilmFilterService` / ImageMagick, `PhotoProcessingConsumer`,
>   fila `PHOTO_QUEUE`) foi **removido** — o filtro agora é aplicado **no app** (client-side).
> - ❌ O `filmStyle` / enum `FilmStyle` foi **removido** do evento.
> - ❌ Os status de foto `PROCESSING` e `FAILED` não existem mais — a foto nasce `READY`.
> - ❌ O upload responde **201 Created** (não 202 Accepted).
>
> **Para implementar o app/frontend, use o [`API_CONTRACT.md`](API_CONTRACT.md)** — é o
> contrato real e atualizado da API. Para o contrato machine-readable, use o OpenAPI
> gerado automaticamente em `GET /api-docs`.
>
> Este arquivo é mantido apenas como registro de como o backend foi originalmente construído.

---

## Sumário

- [Pré-requisitos](#pré-requisitos)
- [Estrutura do projeto](#estrutura-do-projeto)
- [Fase 1 — Ambiente local](#fase-1--ambiente-local)
- [Fase 2 — Configuração do projeto Java](#fase-2--configuração-do-projeto-java)
- [Fase 3 — Pacote common](#fase-3--pacote-common)
- [Fase 4 — Pacote auth](#fase-4--pacote-auth)
- [Fase 5 — Pacote event](#fase-5--pacote-event)
- [Fase 6 — Pacote photo](#fase-6--pacote-photo)
- [Fase 7 — Pacote notification](#fase-7--pacote-notification)
- [Fase 8 — Testes de integração](#fase-8--testes-de-integração)
- [Convenções gerais](#convenções-gerais)

---

## Pré-requisitos

Instale antes de começar:

| Ferramenta | Versão | Link |
|---|---|---|
| Java (JDK) | 21 | https://adoptium.net |
| Docker Desktop | qualquer | https://docker.com/products/docker-desktop |
| IntelliJ IDEA | qualquer edição | https://jetbrains.com/idea |
| Insomnia | qualquer | https://insomnia.rest |
| ImageMagick | qualquer | Mac: `brew install imagemagick` / Ubuntu: `sudo apt install imagemagick` |

---

## Estrutura do projeto

```
eterniza-backend/
├── pom.xml                              ← único POM — todas as dependências aqui
├── docker-compose.yml                   ← infraestrutura local
├── .gitignore
├── README.md
└── src/
    ├── main/
    │   ├── java/com/eterniza/
    │   │   ├── EternizaApplication.java         ← entry point da aplicação
    │   │   │
    │   │   ├── common/                          ← compartilhado por todos os pacotes
    │   │   │   ├── dto/
    │   │   │   │   ├── ApiResponse.java          ← envelope padrão de resposta
    │   │   │   │   └── PageResponse.java         ← envelope para listas paginadas
    │   │   │   ├── exception/
    │   │   │   │   ├── BusinessException.java    ← erro de regra de negócio (400)
    │   │   │   │   ├── NotFoundException.java    ← recurso não encontrado (404)
    │   │   │   │   ├── UnauthorizedException.java← acesso negado (401)
    │   │   │   │   └── GlobalExceptionHandler.java
    │   │   │   └── security/
    │   │   │       └── JwtUtil.java              ← gerar e validar tokens JWT
    │   │   │
    │   │   ├── auth/                            ← autenticação do host e guest
    │   │   │   ├── controller/
    │   │   │   │   └── AuthController.java
    │   │   │   ├── service/
    │   │   │   │   └── AuthService.java
    │   │   │   ├── domain/
    │   │   │   │   └── Host.java                ← entidade JPA
    │   │   │   ├── repository/
    │   │   │   │   └── HostRepository.java
    │   │   │   ├── dto/
    │   │   │   │   ├── RegisterRequest.java
    │   │   │   │   ├── LoginRequest.java
    │   │   │   │   ├── AuthResponse.java
    │   │   │   │   └── GuestSessionRequest.java
    │   │   │   └── security/
    │   │   │       └── SecurityConfig.java
    │   │   │
    │   │   ├── event/                           ← criação e gestão de eventos
    │   │   │   ├── controller/
    │   │   │   │   └── EventController.java
    │   │   │   ├── service/
    │   │   │   │   ├── EventService.java
    │   │   │   │   └── RevealScheduler.java      ← job de revelação automática
    │   │   │   ├── domain/
    │   │   │   │   ├── Event.java
    │   │   │   │   ├── EventStatus.java          ← enum: ACTIVE, REVEALED
    │   │   │   │   └── FilmStyle.java            ← enum: VINTAGE, BLACK_WHITE, COOL, ORIGINAL
    │   │   │   ├── repository/
    │   │   │   │   └── EventRepository.java
    │   │   │   ├── dto/
    │   │   │   │   ├── CreateEventRequest.java
    │   │   │   │   └── EventResponse.java
    │   │   │   └── messaging/
    │   │   │       ├── RabbitMQConfig.java       ← declara filas e exchanges
    │   │   │       └── RevealEventPublisher.java ← publica evento de revelação
    │   │   │
    │   │   ├── photo/                           ← upload, filtros e galeria
    │   │   │   ├── controller/
    │   │   │   │   └── PhotoController.java
    │   │   │   ├── service/
    │   │   │   │   ├── PhotoService.java
    │   │   │   │   └── StorageService.java       ← integração com Cloudflare R2
    │   │   │   ├── domain/
    │   │   │   │   ├── Photo.java
    │   │   │   │   └── PhotoStatus.java          ← enum: PROCESSING, READY, FAILED
    │   │   │   ├── repository/
    │   │   │   │   └── PhotoRepository.java
    │   │   │   ├── dto/
    │   │   │   │   ├── PhotoUploadResponse.java
    │   │   │   │   └── GalleryResponse.java
    │   │   │   ├── consumer/
    │   │   │   │   └── PhotoProcessingConsumer.java ← aplica filtro em background
    │   │   │   └── filter/
    │   │   │       └── FilmFilterService.java    ← aplica filtros via ImageMagick
    │   │   │
    │   │   └── notification/                    ← e-mail e push
    │   │       ├── consumer/
    │   │       │   └── RevealNotificationConsumer.java
    │   │       └── service/
    │   │           └── EmailService.java
    │   │
    │   └── resources/
    │       ├── application.yml                  ← toda a configuração da aplicação
    │       └── db/migration/                    ← scripts SQL executados pelo Flyway
    │           ├── V1__create_hosts.sql
    │           ├── V2__create_events.sql
    │           └── V3__create_photos.sql
    │
    └── test/
        └── java/com/eterniza/
            ├── auth/
            │   └── AuthIntegrationTest.java
            ├── event/
            │   └── EventIntegrationTest.java
            └── photo/
                └── PhotoIntegrationTest.java
```

---

## Fase 1 — Ambiente local

**Objetivo:** ter o banco, cache e fila rodando antes de escrever qualquer código Java.

---

### 1.1 Subir a infraestrutura com Docker

Na pasta raiz do projeto:

```bash
docker compose up -d
```

Verifique que os três containers estão saudáveis:

```bash
docker compose ps
```

Resultado esperado:

```
NAME                 STATUS
eterniza-postgres    Up (healthy)
eterniza-redis       Up (healthy)
eterniza-rabbitmq    Up (healthy)
```

---

### 1.2 Acessar o painel do RabbitMQ

Abra no browser: `http://localhost:15672`

- Usuário: `eterniza`
- Senha: `eterniza123`

As filas ainda estarão vazias — elas são criadas quando a aplicação sobe pela primeira vez.

---

### 1.3 Conectar no banco (opcional)

Para visualizar as tabelas, use o DBeaver, TablePlus, ou a aba Database do IntelliJ:

```
Host:     localhost
Porta:    5432
Banco:    eterniza
Usuário:  eterniza
Senha:    eterniza123
```

Você não precisa criar nenhuma tabela à mão. O Flyway faz isso automaticamente.

---

### 1.4 Importar o projeto no IntelliJ

1. `File → Open` → selecione a pasta raiz do projeto
2. O IntelliJ detecta o `pom.xml` e importa automaticamente
3. Aguarde o download das dependências (primeira vez: ~5 min)
4. Confirme que aparece **um único módulo** no painel Maven (lado direito)

---

## Fase 2 — Configuração do projeto Java

**Objetivo:** criar o entry point da aplicação e validar que tudo sobe sem erro.

---

### 2.1 `EternizaApplication.java`

Crie em `src/main/java/com/eterniza/`:

```java
package com.eterniza;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling   // habilita o job de revelação automática
public class EternizaApplication {
    public static void main(String[] args) {
        SpringApplication.run(EternizaApplication.class, args);
    }
}
```

---

### 2.2 Verificar que a aplicação sobe

Clique em **Run** no IntelliJ na classe `EternizaApplication`.

No console, você deve ver:

```
Started EternizaApplication in X.XXX seconds
```

Se aparecer erro de conexão com o banco, confirme que o Docker está rodando com `docker compose ps`.

---

### 2.3 Migrations do banco — scripts SQL

O Flyway executa os scripts em ordem numérica. Crie os três arquivos abaixo em `src/main/resources/db/migration/`:

**`V1__create_hosts.sql`**
```sql
CREATE TABLE hosts (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email       VARCHAR(255) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    name        VARCHAR(255) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_hosts_email ON hosts(email);
```

**`V2__create_events.sql`**
```sql
CREATE TYPE event_status AS ENUM ('ACTIVE', 'REVEALED');
CREATE TYPE film_style   AS ENUM ('VINTAGE', 'BLACK_WHITE', 'COOL', 'ORIGINAL');

CREATE TABLE events (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    host_id      UUID         NOT NULL,
    name         VARCHAR(255) NOT NULL,
    slug         VARCHAR(36)  NOT NULL UNIQUE,
    film_style   film_style   NOT NULL DEFAULT 'VINTAGE',
    status       event_status NOT NULL DEFAULT 'ACTIVE',
    reveal_at    TIMESTAMP    NOT NULL,
    guest_limit  INT          NOT NULL DEFAULT 5,
    guest_count  INT          NOT NULL DEFAULT 0,
    photo_count  INT          NOT NULL DEFAULT 0,
    created_at   TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_events_host_id   ON events(host_id);
CREATE INDEX idx_events_slug      ON events(slug);
CREATE INDEX idx_events_reveal_at ON events(reveal_at) WHERE status = 'ACTIVE';
```

**`V3__create_photos.sql`**
```sql
CREATE TYPE photo_status AS ENUM ('PROCESSING', 'READY', 'FAILED');

CREATE TABLE photos (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id         UUID         NOT NULL,
    guest_device_id  VARCHAR(255) NOT NULL,
    guest_name       VARCHAR(255) NOT NULL,
    original_key     VARCHAR(500) NOT NULL,
    filtered_key     VARCHAR(500),
    status           photo_status NOT NULL DEFAULT 'PROCESSING',
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT fk_photos_event FOREIGN KEY (event_id) REFERENCES events(id)
);

CREATE INDEX idx_photos_event_id ON photos(event_id);
CREATE INDEX idx_photos_status   ON photos(status);
```

Toda vez que a aplicação reiniciar, o Flyway verifica se há scripts novos e os executa automaticamente.

---

## Fase 3 — Pacote common

**Objetivo:** criar os componentes compartilhados — envelope de resposta, exceções e JWT.  
Esses são usados por **todos os outros pacotes**.

---

### 3.1 `ApiResponse.java`

```java
package com.eterniza.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.Instant;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;

    @Builder.Default
    private final Instant timestamp = Instant.now();

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().success(true).data(data).build();
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return ApiResponse.<T>builder().success(true).message(message).data(data).build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder().success(false).message(message).build();
    }
}
```

---

### 3.2 Exceções

**`BusinessException.java`** — erro de regra de negócio, retorna HTTP 400:
```java
package com.eterniza.common.exception;

public class BusinessException extends RuntimeException {
    public BusinessException(String message) { super(message); }
}
```

**`NotFoundException.java`** — recurso não encontrado, retorna HTTP 404:
```java
package com.eterniza.common.exception;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String resource, Object id) {
        super("%s não encontrado: %s".formatted(resource, id));
    }
}
```

**`UnauthorizedException.java`** — acesso negado, retorna HTTP 401:
```java
package com.eterniza.common.exception;

public class UnauthorizedException extends RuntimeException {
    public UnauthorizedException(String message) { super(message); }
}
```

---

### 3.3 `GlobalExceptionHandler.java`

Captura todas as exceções e retorna no formato `ApiResponse`:

```java
package com.eterniza.common.exception;

import com.eterniza.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        return ResponseEntity.badRequest().body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnauthorized(UnauthorizedException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest().body(ApiResponse.error(message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Erro inesperado", ex);
        return ResponseEntity.internalServerError().body(ApiResponse.error("Erro interno. Tente novamente."));
    }
}
```

---

### 3.4 `JwtUtil.java`

Gera e valida tokens JWT. Usado pelo `auth` (para gerar) e pela `SecurityConfig` (para validar nas requisições):

```java
package com.eterniza.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
public class JwtUtil {

    private final SecretKey key;
    private final long expirationMs;
    private final long guestExpirationMs;

    public JwtUtil(
            @Value("${eterniza.jwt.secret}") String secret,
            @Value("${eterniza.jwt.expiration-ms:86400000}") long expirationMs,
            @Value("${eterniza.jwt.guest-expiration-ms:604800000}") long guestExpirationMs) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expirationMs = expirationMs;
        this.guestExpirationMs = guestExpirationMs;
    }

    public String generateHostToken(String hostId, String email) {
        return build(hostId, Map.of("email", email, "role", "HOST"), expirationMs);
    }

    public String generateGuestToken(String deviceId, String displayName, String eventId) {
        return build(deviceId, Map.of("displayName", displayName, "eventId", eventId, "role", "GUEST"), guestExpirationMs);
    }

    private String build(String subject, Map<String, Object> claims, long expMs) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claims(claims)
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expMs))
                .signWith(key)
                .compact();
    }

    public Claims extractClaims(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }

    public boolean isValid(String token) {
        try {
            extractClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("Token inválido: {}", e.getMessage());
            return false;
        }
    }

    public String extractSubject(String token) { return extractClaims(token).getSubject(); }
    public String extractRole(String token)    { return (String) extractClaims(token).get("role"); }
    public String extractEventId(String token) { return (String) extractClaims(token).get("eventId"); }
}
```

---

### 3.5 Verificação da Fase 3

Rode a aplicação. Se não houver erros no console relacionados a `common`, a fase está concluída.

---

## Fase 4 — Pacote auth

**Objetivo:** registro e login do host com JWT. Criação de sessão anônima para o guest.

**Endpoints:**
- `POST /api/auth/register` — cadastrar host
- `POST /api/auth/login` — login do host
- `POST /api/auth/guest/session` — criar sessão anônima para guest

---

### 4.1 `Host.java`

```java
package com.eterniza.auth.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "hosts")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Host {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String name;

    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp   private Instant updatedAt;
}
```

---

### 4.2 `HostRepository.java`

```java
package com.eterniza.auth.repository;

import com.eterniza.auth.domain.Host;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HostRepository extends JpaRepository<Host, UUID> {
    Optional<Host> findByEmail(String email);
    boolean existsByEmail(String email);
}
```

---

### 4.3 DTOs

**`RegisterRequest.java`**
```java
package com.eterniza.auth.dto;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank(message = "Nome é obrigatório") String name,
        @Email @NotBlank(message = "E-mail é obrigatório") String email,
        @NotBlank @Size(min = 8, message = "Senha deve ter no mínimo 8 caracteres") String password
) {}
```

**`LoginRequest.java`**
```java
package com.eterniza.auth.dto;

import jakarta.validation.constraints.*;

public record LoginRequest(
        @Email @NotBlank String email,
        @NotBlank String password
) {}
```

**`AuthResponse.java`**
```java
package com.eterniza.auth.dto;

public record AuthResponse(String token, String name, String email) {}
```

**`GuestSessionRequest.java`**
```java
package com.eterniza.auth.dto;

import jakarta.validation.constraints.*;

public record GuestSessionRequest(
        @NotBlank(message = "Nome de exibição é obrigatório")
        @Size(max = 30, message = "Nome deve ter no máximo 30 caracteres")
        String displayName,

        @NotBlank(message = "ID do evento é obrigatório")
        String eventId,

        @NotBlank(message = "Device ID é obrigatório")
        String deviceId
) {}
```

---

### 4.4 `AuthService.java`

```java
package com.eterniza.auth.service;

import com.eterniza.auth.domain.Host;
import com.eterniza.auth.dto.*;
import com.eterniza.auth.repository.HostRepository;
import com.eterniza.common.exception.BusinessException;
import com.eterniza.common.exception.UnauthorizedException;
import com.eterniza.common.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final HostRepository hostRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthResponse register(RegisterRequest req) {
        if (hostRepository.existsByEmail(req.email())) {
            throw new BusinessException("E-mail já cadastrado");
        }
        Host host = hostRepository.save(Host.builder()
                .name(req.name())
                .email(req.email())
                .password(passwordEncoder.encode(req.password()))
                .build());

        return new AuthResponse(
                jwtUtil.generateHostToken(host.getId().toString(), host.getEmail()),
                host.getName(),
                host.getEmail()
        );
    }

    public AuthResponse login(LoginRequest req) {
        Host host = hostRepository.findByEmail(req.email())
                .orElseThrow(() -> new UnauthorizedException("Credenciais inválidas"));

        if (!passwordEncoder.matches(req.password(), host.getPassword())) {
            throw new UnauthorizedException("Credenciais inválidas");
        }
        return new AuthResponse(
                jwtUtil.generateHostToken(host.getId().toString(), host.getEmail()),
                host.getName(),
                host.getEmail()
        );
    }

    public String createGuestSession(GuestSessionRequest req) {
        return jwtUtil.generateGuestToken(req.deviceId(), req.displayName(), req.eventId());
    }
}
```

---

### 4.5 `SecurityConfig.java`

```java
package com.eterniza.auth.security;

import com.eterniza.common.security.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    // Rotas públicas — não exigem token
    private static final List<String> PUBLIC = List.of(
            "/api/auth/register",
            "/api/auth/login",
            "/api/auth/guest/session",
            "/api/events/slug/",          // guest acessa evento pelo slug
            "/api/photos/gallery/",       // guest visualiza galeria
            "/swagger-ui",
            "/swagger-ui.html",
            "/api-docs"
    );

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC.toArray(String[]::new)).permitAll()
                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Filtro JWT — valida o token em toda requisição autenticada
    @Component
    @RequiredArgsConstructor
    public static class JwtAuthFilter extends OncePerRequestFilter {

        private final JwtUtil jwtUtil;

        @Override
        protected void doFilterInternal(HttpServletRequest req,
                                        HttpServletResponse res,
                                        FilterChain chain) throws ServletException, IOException {
            String header = req.getHeader("Authorization");

            if (header != null && header.startsWith("Bearer ")) {
                String token = header.replace("Bearer ", "");
                if (!jwtUtil.isValid(token)) {
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    return;
                }
                // Aqui você poderia popular o SecurityContext se precisar
                // de controle de roles mais granular no futuro
            }
            chain.doFilter(req, res);
        }
    }
}
```

---

### 4.6 `AuthController.java`

```java
package com.eterniza.auth.controller;

import com.eterniza.auth.dto.*;
import com.eterniza.auth.service.AuthService;
import com.eterniza.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Autenticação do host e sessão do guest")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Registrar novo host")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok("Conta criada com sucesso", authService.register(req));
    }

    @PostMapping("/login")
    @Operation(summary = "Login do host")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(authService.login(req));
    }

    @PostMapping("/guest/session")
    @Operation(summary = "Criar sessão anônima para guest")
    public ApiResponse<String> guestSession(@Valid @RequestBody GuestSessionRequest req) {
        return ApiResponse.ok(authService.createGuestSession(req));
    }
}
```

---

### 4.7 Testar a Fase 4

Acesse `http://localhost:8080/swagger-ui.html` e teste em ordem:

**1. Registrar:**
```json
POST /api/auth/register
{ "name": "Lucas", "email": "lucas@eterniza.com", "password": "senha1234" }
```
Esperado: `201` com token JWT.

**2. Login:**
```json
POST /api/auth/login
{ "email": "lucas@eterniza.com", "password": "senha1234" }
```
Esperado: `200` com token JWT.

**3. Sessão guest:**
```json
POST /api/auth/guest/session
{ "displayName": "Ana", "eventId": "qualquer-uuid", "deviceId": "device-uuid-123" }
```
Esperado: `200` com token guest.

---

## Fase 5 — Pacote event

**Objetivo:** criar e gerenciar eventos, gerar o slug do QR code, revelar eventos automaticamente.

**Endpoints:**
- `POST /api/events` — criar evento (host autenticado)
- `GET  /api/events/slug/{slug}` — buscar evento pelo slug (público)
- `GET  /api/events/my` — listar eventos do host autenticado

---

### 5.1 Enums

**`EventStatus.java`**
```java
package com.eterniza.event.domain;
public enum EventStatus { ACTIVE, REVEALED }
```

**`FilmStyle.java`**
```java
package com.eterniza.event.domain;
public enum FilmStyle { VINTAGE, BLACK_WHITE, COOL, ORIGINAL }
```

---

### 5.2 `Event.java`

```java
package com.eterniza.event.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "events")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID hostId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private FilmStyle filmStyle = FilmStyle.VINTAGE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private EventStatus status = EventStatus.ACTIVE;

    @Column(nullable = false)
    private Instant revealAt;

    @Column(nullable = false) @Builder.Default private int guestLimit = 5;
    @Column(nullable = false) @Builder.Default private int guestCount = 0;
    @Column(nullable = false) @Builder.Default private int photoCount = 0;

    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp   private Instant updatedAt;

    public boolean isRevealed()         { return status == EventStatus.REVEALED; }
    public boolean isGuestLimitReached(){ return guestCount >= guestLimit; }
}
```

---

### 5.3 `EventRepository.java`

```java
package com.eterniza.event.repository;

import com.eterniza.event.domain.Event;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EventRepository extends JpaRepository<Event, UUID> {
    Optional<Event> findBySlug(String slug);
    List<Event> findByHostIdOrderByCreatedAtDesc(UUID hostId);

    @Query("SELECT e FROM Event e WHERE e.status = 'ACTIVE' AND e.revealAt <= :now")
    List<Event> findEventsReadyToReveal(Instant now);
}
```

---

### 5.4 DTOs

**`CreateEventRequest.java`**
```java
package com.eterniza.event.dto;

import com.eterniza.event.domain.FilmStyle;
import jakarta.validation.constraints.*;
import java.time.Instant;

public record CreateEventRequest(
        @NotBlank(message = "Nome do evento é obrigatório") String name,
        @NotNull(message = "Estilo de filme é obrigatório") FilmStyle filmStyle,
        @NotNull @Future(message = "Revelação deve ser no futuro") Instant revealAt,
        Integer guestLimit
) {}
```

**`EventResponse.java`**
```java
package com.eterniza.event.dto;

import com.eterniza.event.domain.EventStatus;
import com.eterniza.event.domain.FilmStyle;
import java.time.Instant;
import java.util.UUID;

public record EventResponse(
        UUID id, String name, String slug, String qrCodeUrl,
        FilmStyle filmStyle, EventStatus status, Instant revealAt,
        int guestLimit, int guestCount, int photoCount, Instant createdAt
) {}
```

---

### 5.5 `EventService.java`

```java
package com.eterniza.event.service;

import com.eterniza.common.exception.BusinessException;
import com.eterniza.common.exception.NotFoundException;
import com.eterniza.event.domain.Event;
import com.eterniza.event.domain.EventStatus;
import com.eterniza.event.dto.CreateEventRequest;
import com.eterniza.event.dto.EventResponse;
import com.eterniza.event.messaging.RevealEventPublisher;
import com.eterniza.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository eventRepository;
    private final RevealEventPublisher revealPublisher;

    @Value("${eterniza.app.web-url}")
    private String webUrl;

    @Transactional
    public EventResponse create(CreateEventRequest req, UUID hostId) {
        Event event = eventRepository.save(Event.builder()
                .hostId(hostId)
                .name(req.name())
                .slug(UUID.randomUUID().toString())
                .filmStyle(req.filmStyle())
                .revealAt(req.revealAt())
                .guestLimit(req.guestLimit() != null ? req.guestLimit() : 5)
                .build());
        return toResponse(event);
    }

    public EventResponse findBySlug(String slug) {
        return toResponse(eventRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Evento", slug)));
    }

    public List<EventResponse> findByHost(UUID hostId) {
        return eventRepository.findByHostIdOrderByCreatedAtDesc(hostId)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public void incrementGuestCount(String slug) {
        Event event = eventRepository.findBySlug(slug)
                .orElseThrow(() -> new NotFoundException("Evento", slug));
        if (event.isGuestLimitReached()) {
            throw new BusinessException("Limite de convidados atingido");
        }
        event.setGuestCount(event.getGuestCount() + 1);
    }

    @Transactional
    public void reveal(UUID eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new NotFoundException("Evento", eventId));
        if (event.isRevealed()) return; // idempotente
        event.setStatus(EventStatus.REVEALED);
        revealPublisher.publish(event.getId().toString(), event.getHostId().toString());
    }

    // Job chamado pelo RevealScheduler
    public void checkAndRevealPending() {
        eventRepository.findEventsReadyToReveal(Instant.now())
                .forEach(e -> reveal(e.getId()));
    }

    private EventResponse toResponse(Event e) {
        return new EventResponse(e.getId(), e.getName(), e.getSlug(),
                "%s/e/%s".formatted(webUrl, e.getSlug()),
                e.getFilmStyle(), e.getStatus(), e.getRevealAt(),
                e.getGuestLimit(), e.getGuestCount(), e.getPhotoCount(), e.getCreatedAt());
    }
}
```

---

### 5.6 `RevealScheduler.java`

```java
package com.eterniza.event.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class RevealScheduler {

    private final EventService eventService;

    @Scheduled(fixedDelay = 60_000) // roda a cada 60 segundos
    public void run() {
        log.debug("Verificando eventos para revelar...");
        eventService.checkAndRevealPending();
    }
}
```

---

### 5.7 `RabbitMQConfig.java`

```java
package com.eterniza.event.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE         = "eterniza.events";
    public static final String REVEAL_QUEUE     = "eterniza.event.revealed";
    public static final String REVEAL_KEY       = "event.revealed";
    public static final String PHOTO_QUEUE      = "eterniza.photo.process";
    public static final String PHOTO_KEY        = "photo.uploaded";

    @Bean TopicExchange eventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean Queue revealQueue() { return QueueBuilder.durable(REVEAL_QUEUE).build(); }
    @Bean Queue photoQueue()  { return QueueBuilder.durable(PHOTO_QUEUE).build(); }

    @Bean Binding revealBinding(Queue revealQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(revealQueue).to(eventsExchange).with(REVEAL_KEY);
    }

    @Bean Binding photoBinding(Queue photoQueue, TopicExchange eventsExchange) {
        return BindingBuilder.bind(photoQueue).to(eventsExchange).with(PHOTO_KEY);
    }

    @Bean Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

---

### 5.8 `RevealEventPublisher.java`

```java
package com.eterniza.event.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class RevealEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(String eventId, String hostId) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.REVEAL_KEY,
                Map.of("eventId", eventId, "hostId", hostId)
        );
    }
}
```

---

### 5.9 `EventController.java`

```java
package com.eterniza.event.controller;

import com.eterniza.common.dto.ApiResponse;
import com.eterniza.common.security.JwtUtil;
import com.eterniza.event.dto.CreateEventRequest;
import com.eterniza.event.dto.EventResponse;
import com.eterniza.event.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/events")
@RequiredArgsConstructor
@Tag(name = "Events", description = "Gerenciamento de eventos")
public class EventController {

    private final EventService eventService;
    private final JwtUtil jwtUtil;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Criar evento")
    public ApiResponse<EventResponse> create(
            @Valid @RequestBody CreateEventRequest req,
            @RequestHeader("Authorization") String auth) {
        UUID hostId = UUID.fromString(jwtUtil.extractSubject(auth.replace("Bearer ", "")));
        return ApiResponse.ok("Evento criado", eventService.create(req, hostId));
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Buscar evento pelo slug do QR code")
    public ApiResponse<EventResponse> findBySlug(@PathVariable String slug) {
        return ApiResponse.ok(eventService.findBySlug(slug));
    }

    @GetMapping("/my")
    @Operation(summary = "Listar meus eventos")
    public ApiResponse<List<EventResponse>> myEvents(
            @RequestHeader("Authorization") String auth) {
        UUID hostId = UUID.fromString(jwtUtil.extractSubject(auth.replace("Bearer ", "")));
        return ApiResponse.ok(eventService.findByHost(hostId));
    }
}
```

---

## Fase 6 — Pacote photo

**Objetivo:** receber o upload das fotos dos guests, armazenar no Cloudflare R2 e processar o filtro de película em background.

**Endpoints:**
- `POST /api/photos/upload` — enviar foto (guest autenticado)
- `GET  /api/photos/gallery/{eventId}` — galeria do evento

---

### 6.1 `PhotoStatus.java`

```java
package com.eterniza.photo.domain;
public enum PhotoStatus { PROCESSING, READY, FAILED }
```

---

### 6.2 `Photo.java`

```java
package com.eterniza.photo.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "photos")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Photo {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false) private UUID eventId;
    @Column(nullable = false) private String guestDeviceId;
    @Column(nullable = false) private String guestName;
    @Column(nullable = false) private String originalKey;
    private String filteredKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PhotoStatus status = PhotoStatus.PROCESSING;

    @CreationTimestamp private Instant createdAt;
}
```

---

### 6.3 `PhotoRepository.java`

```java
package com.eterniza.photo.repository;

import com.eterniza.photo.domain.Photo;
import com.eterniza.photo.domain.PhotoStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PhotoRepository extends JpaRepository<Photo, UUID> {
    List<Photo> findByEventIdAndStatus(UUID eventId, PhotoStatus status);
    long countByEventId(UUID eventId);
}
```

---

### 6.4 `StorageService.java`

```java
package com.eterniza.photo.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;

@Slf4j
@Service
public class StorageService {

    @Value("${eterniza.r2.endpoint}") private String endpoint;
    @Value("${eterniza.r2.access-key}") private String accessKey;
    @Value("${eterniza.r2.secret-key}") private String secretKey;
    @Value("${eterniza.r2.bucket}") private String bucket;
    @Value("${eterniza.r2.public-url}") private String publicUrl;

    private S3Client s3;

    @PostConstruct
    public void init() {
        s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .build();
    }

    public String upload(String key, MultipartFile file) throws IOException {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket).key(key).contentType(file.getContentType()).build(),
                RequestBody.fromBytes(file.getBytes()));
        return publicUrl + "/" + key;
    }

    public String upload(String key, byte[] bytes, String contentType) {
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket).key(key).contentType(contentType).build(),
                RequestBody.fromBytes(bytes));
        return publicUrl + "/" + key;
    }

    public byte[] download(String key) throws IOException {
        return s3.getObjectAsBytes(GetObjectRequest.builder()
                .bucket(bucket).key(key).build()).asByteArray();
    }
}
```

---

### 6.5 `FilmFilterService.java`

```java
package com.eterniza.photo.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Slf4j
@Service
public class FilmFilterService {

    public byte[] apply(byte[] original, String filmStyle) throws IOException, InterruptedException {
        Path input  = Files.createTempFile("et-in-",  ".jpg");
        Path output = Files.createTempFile("et-out-", ".jpg");
        try {
            Files.write(input, original);
            List<String> cmd = buildCommand(input.toString(), output.toString(), filmStyle);
            int exit = new ProcessBuilder(cmd).redirectErrorStream(true).start().waitFor();
            if (exit != 0) { log.warn("ImageMagick saiu com código {}", exit); return original; }
            return Files.readAllBytes(output);
        } finally {
            Files.deleteIfExists(input);
            Files.deleteIfExists(output);
        }
    }

    private List<String> buildCommand(String in, String out, String style) {
        return switch (style) {
            case "VINTAGE"     -> List.of("convert", in, "-modulate", "100,80,100", "-colorize", "10,5,0", "-contrast-stretch", "0.5%", out);
            case "BLACK_WHITE" -> List.of("convert", in, "-colorspace", "Gray", "-contrast-stretch", "1%", out);
            case "COOL"        -> List.of("convert", in, "-modulate", "100,90,105", "-colorize", "0,3,12", out);
            default            -> List.of("convert", in, out); // ORIGINAL
        };
    }
}
```

---

### 6.6 `PhotoService.java`

```java
package com.eterniza.photo.service;

import com.eterniza.common.exception.BusinessException;
import com.eterniza.common.security.JwtUtil;
import com.eterniza.event.repository.EventRepository;
import com.eterniza.photo.domain.Photo;
import com.eterniza.photo.domain.PhotoStatus;
import com.eterniza.photo.dto.GalleryResponse;
import com.eterniza.photo.dto.PhotoUploadResponse;
import com.eterniza.photo.repository.PhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.eterniza.event.messaging.RabbitMQConfig.*;

@Service
@RequiredArgsConstructor
public class PhotoService {

    private final PhotoRepository photoRepository;
    private final EventRepository eventRepository;
    private final StorageService storageService;
    private final RabbitTemplate rabbitTemplate;
    private final JwtUtil jwtUtil;

    private static final List<String> ALLOWED = List.of("image/jpeg", "image/png", "image/webp");

    @Transactional
    public PhotoUploadResponse upload(MultipartFile file, String guestToken, String eventId) throws IOException {
        if (file.isEmpty())                             throw new BusinessException("Arquivo vazio");
        if (!ALLOWED.contains(file.getContentType()))  throw new BusinessException("Formato inválido. Envie JPEG, PNG ou WebP");
        if (file.getSize() > 20 * 1024 * 1024)        throw new BusinessException("Arquivo muito grande. Máximo 20MB");

        var claims    = jwtUtil.extractClaims(guestToken.replace("Bearer ", ""));
        String deviceId  = claims.getSubject();
        String guestName = (String) claims.get("displayName");

        String photoId     = UUID.randomUUID().toString();
        String originalKey = "events/%s/originals/%s.jpg".formatted(eventId, photoId);

        storageService.upload(originalKey, file);

        // Busca filmStyle do evento para enviar na mensagem
        String filmStyle = eventRepository.findById(UUID.fromString(eventId))
                .map(e -> e.getFilmStyle().name())
                .orElse("ORIGINAL");

        Photo photo = photoRepository.save(Photo.builder()
                .eventId(UUID.fromString(eventId))
                .guestDeviceId(deviceId)
                .guestName(guestName)
                .originalKey(originalKey)
                .build());

        // Publica para processamento assíncrono do filtro
        rabbitTemplate.convertAndSend(EXCHANGE, PHOTO_KEY, Map.of(
                "photoId", photo.getId().toString(),
                "originalKey", originalKey,
                "filmStyle", filmStyle
        ));

        return new PhotoUploadResponse(photo.getId(), "Foto recebida!");
    }

    public GalleryResponse getGallery(String eventId, boolean isRevealed) {
        List<Photo> photos = photoRepository.findByEventIdAndStatus(
                UUID.fromString(eventId), PhotoStatus.READY);

        if (!isRevealed) {
            return new GalleryResponse(false, photos.size(), List.of());
        }

        List<String> urls = photos.stream()
                .map(p -> p.getFilteredKey() != null ? p.getFilteredKey() : p.getOriginalKey())
                .toList();

        return new GalleryResponse(true, photos.size(), urls);
    }
}
```

---

### 6.7 `PhotoProcessingConsumer.java`

```java
package com.eterniza.photo.consumer;

import com.eterniza.photo.domain.Photo;
import com.eterniza.photo.domain.PhotoStatus;
import com.eterniza.photo.filter.FilmFilterService;
import com.eterniza.photo.repository.PhotoRepository;
import com.eterniza.photo.service.StorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

import static com.eterniza.event.messaging.RabbitMQConfig.PHOTO_QUEUE;

@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoProcessingConsumer {

    private final PhotoRepository photoRepository;
    private final StorageService storageService;
    private final FilmFilterService filmFilterService;

    @RabbitListener(queues = PHOTO_QUEUE)
    public void process(Map<String, String> msg) {
        String photoId     = msg.get("photoId");
        String originalKey = msg.get("originalKey");
        String filmStyle   = msg.get("filmStyle");

        log.info("Processando foto {} com filtro {}", photoId, filmStyle);

        Photo photo = photoRepository.findById(UUID.fromString(photoId)).orElse(null);
        if (photo == null) { log.warn("Foto não encontrada: {}", photoId); return; }

        try {
            byte[] original = storageService.download(originalKey);
            byte[] filtered = filmFilterService.apply(original, filmStyle);

            String filteredKey = originalKey.replace("/originals/", "/filtered/");
            storageService.upload(filteredKey, filtered, "image/jpeg");

            photo.setFilteredKey(filteredKey);
            photo.setStatus(PhotoStatus.READY);
        } catch (Exception e) {
            log.error("Erro ao processar foto {}", photoId, e);
            photo.setStatus(PhotoStatus.FAILED);
        }
        photoRepository.save(photo);
    }
}
```

---

### 6.8 DTOs e Controller

**`PhotoUploadResponse.java`**
```java
package com.eterniza.photo.dto;
import java.util.UUID;
public record PhotoUploadResponse(UUID photoId, String message) {}
```

**`GalleryResponse.java`**
```java
package com.eterniza.photo.dto;
import java.util.List;
public record GalleryResponse(boolean revealed, int totalPhotos, List<String> photoUrls) {}
```

**`PhotoController.java`**
```java
package com.eterniza.photo.controller;

import com.eterniza.common.dto.ApiResponse;
import com.eterniza.event.repository.EventRepository;
import com.eterniza.photo.dto.GalleryResponse;
import com.eterniza.photo.dto.PhotoUploadResponse;
import com.eterniza.photo.service.PhotoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/photos")
@RequiredArgsConstructor
@Tag(name = "Photos", description = "Upload e galeria de fotos")
public class PhotoController {

    private final PhotoService photoService;
    private final EventRepository eventRepository;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Enviar foto (guest)")
    public ApiResponse<PhotoUploadResponse> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("eventId") String eventId,
            @RequestHeader("Authorization") String guestToken) throws IOException {
        return ApiResponse.ok(photoService.upload(file, guestToken, eventId));
    }

    @GetMapping("/gallery/{eventId}")
    @Operation(summary = "Galeria do evento")
    public ApiResponse<GalleryResponse> gallery(@PathVariable String eventId) {
        boolean revealed = eventRepository.findById(UUID.fromString(eventId))
                .map(e -> e.getStatus().name().equals("REVEALED"))
                .orElse(false);
        return ApiResponse.ok(photoService.getGallery(eventId, revealed));
    }
}
```

---

## Fase 7 — Pacote notification

**Objetivo:** consumir a fila de revelação e enviar e-mail ao host.

---

### 7.1 `EmailService.java`

```java
package com.eterniza.notification.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${eterniza.mail.from}") private String from;
    @Value("${eterniza.app.web-url}") private String webUrl;

    public void sendRevealEmail(String toEmail, String eventId, String eventName) {
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(from);
            msg.setTo(toEmail);
            msg.setSubject("Sua galeria Eterniza foi revelada!");
            msg.setText("""
                Olá!

                A galeria do evento "%s" está disponível agora.
                Todas as fotos dos seus convidados foram reveladas.

                Acesse aqui: %s/e/%s

                Eterniza — cada momento, para sempre.
                """.formatted(eventName, webUrl, eventId));
            mailSender.send(msg);
            log.info("E-mail enviado para {}", toEmail);
        } catch (Exception e) {
            log.error("Falha ao enviar e-mail para {}", toEmail, e);
        }
    }
}
```

---

### 7.2 `RevealNotificationConsumer.java`

```java
package com.eterniza.notification.consumer;

import com.eterniza.auth.repository.HostRepository;
import com.eterniza.event.repository.EventRepository;
import com.eterniza.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

import static com.eterniza.event.messaging.RabbitMQConfig.REVEAL_QUEUE;

@Slf4j
@Component
@RequiredArgsConstructor
public class RevealNotificationConsumer {

    private final EmailService emailService;
    private final HostRepository hostRepository;
    private final EventRepository eventRepository;

    @RabbitListener(queues = REVEAL_QUEUE)
    public void onReveal(Map<String, String> msg) {
        String eventId = msg.get("eventId");
        String hostId  = msg.get("hostId");

        var event = eventRepository.findById(UUID.fromString(eventId)).orElse(null);
        var host  = hostRepository.findById(UUID.fromString(hostId)).orElse(null);

        if (event == null || host == null) {
            log.warn("Evento ou host não encontrado para notificação: eventId={}", eventId);
            return;
        }

        emailService.sendRevealEmail(host.getEmail(), eventId, event.getName());
    }
}
```

---

## Fase 8 — Testes de integração

**Objetivo:** garantir que os fluxos mais críticos funcionam de ponta a ponta com banco real.

---

### 8.1 `AuthIntegrationTest.java`

```java
package com.eterniza.auth;

import com.eterniza.auth.dto.LoginRequest;
import com.eterniza.auth.dto.RegisterRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AuthIntegrationTest {

    @Container
    static PostgreSQLContainer<?> pg = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("eterniza_test")
            .withUsername("eterniza")
            .withPassword("eterniza123");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url",      pg::getJdbcUrl);
        r.add("spring.datasource.username", pg::getUsername);
        r.add("spring.datasource.password", pg::getPassword);
        r.add("spring.rabbitmq.host",       () -> "localhost"); // mock ou desabilitar
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    @Test
    void deveRegistrarEFazerLogin() throws Exception {
        var reg = new RegisterRequest("Lucas", "lucas@test.com", "senha1234");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(reg)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.token").isNotEmpty());

        var login = new LoginRequest("lucas@test.com", "senha1234");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    void deveRejeitarSenhaErrada() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(new LoginRequest("x@x.com", "errada"))))
                .andExpect(status().isUnauthorized());
    }
}
```

---

## Convenções gerais

### Formato de resposta

Toda resposta segue o envelope `ApiResponse`:

```json
{
  "success": true,
  "message": "opcional",
  "data": { ... },
  "timestamp": "2025-01-01T00:00:00Z"
}
```

### Porta única

Com o monolito, a aplicação roda toda na porta `8080`. Não há portas diferentes por serviço.

### Swagger

Acesse `http://localhost:8080/swagger-ui.html` para ver e testar todos os endpoints.

### Variáveis sensíveis

Nunca commite senhas. Em produção, use variáveis de ambiente mapeadas no `application.yml` via `${NOME_DA_VAR}`.

### Ordem de implementação

```
Fase 1 (Docker) → Fase 2 (EternizaApplication + migrations)
→ Fase 3 (common) → Fase 4 (auth) → Fase 5 (event)
→ Fase 6 (photo) → Fase 7 (notification) → Fase 8 (testes)
```

Cada fase pode ser verificada individualmente antes de avançar.
