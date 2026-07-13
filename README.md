# Eterniza — Back-end

Câmera descartável digital para eventos.

## Estrutura do projeto

```
eterniza-backend/
├── pom.xml                          ← único POM do projeto
├── docker-compose.yml               ← PostgreSQL + Redis + RabbitMQ
├── src/
│   ├── main/
│   │   ├── java/com/eterniza/
│   │   │   ├── EternizaApplication.java     ← entry point
│   │   │   ├── common/                      ← compartilhado por todos
│   │   │   │   ├── dto/
│   │   │   │   ├── exception/
│   │   │   │   └── security/
│   │   │   ├── auth/                        ← autenticação
│   │   │   │   ├── controller/
│   │   │   │   ├── service/
│   │   │   │   ├── domain/
│   │   │   │   ├── repository/
│   │   │   │   ├── dto/
│   │   │   │   └── security/
│   │   │   ├── event/                       ← eventos e QR code
│   │   │   │   ├── controller/
│   │   │   │   ├── service/
│   │   │   │   ├── domain/
│   │   │   │   ├── repository/
│   │   │   │   ├── dto/
│   │   │   │   └── messaging/
│   │   │   ├── photo/                       ← upload e galeria
│   │   │   │   ├── controller/
│   │   │   │   ├── service/
│   │   │   │   ├── domain/
│   │   │   │   ├── repository/
│   │   │   │   ├── dto/
│   │   │   │   ├── consumer/
│   │   │   │   └── filter/
│   │   │   └── notification/                ← e-mail e push
│   │   │       ├── consumer/
│   │   │       └── service/
│   │   └── resources/
│   │       ├── application.yml
│   │       └── db/migration/                ← scripts SQL do Flyway
│   └── test/
│       └── java/com/eterniza/
│           ├── auth/
│           ├── event/
│           └── photo/
```

## Como rodar localmente

### Pré-requisitos
- Java 21
- Docker Desktop
- IntelliJ IDEA

### 1. Subir a infraestrutura
```bash
docker compose up -d
```

### 2. Rodar a aplicação
Abra o IntelliJ, importe a pasta raiz e clique em Run na classe `EternizaApplication`.

### 3. Acessar o Swagger
http://localhost:8080/swagger-ui.html

### Painel RabbitMQ
http://localhost:15672 (eterniza / eterniza123)
