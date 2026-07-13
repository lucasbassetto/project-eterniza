# Fase 2 — Configuração do projeto Java — Specification

## Problem Statement

O monolito Eterniza tem infraestrutura local (Fase 1) e POM configurados, mas ainda não possui um entry point Spring Boot nem as migrations de banco. Sem isso, nada do restante do backend (auth, event, photo, notification) pode ser implementado ou testado.

## Goals

- [ ] Aplicação Spring Boot sobe sem erro, conectando-se ao Postgres/Redis/RabbitMQ já disponíveis (Fase 1)
- [ ] Flyway aplica as 3 migrations (hosts, events, photos) automaticamente na inicialização, criando o schema completo do banco

## Out of Scope

| Item | Motivo |
| --- | --- |
| Pacotes `common`, `auth`, `event`, `photo`, `notification` | Fases 3-7 do BACKEND_SPEC.md — dependem deste entry point existir primeiro |
| Testes de integração | Fase 8 do BACKEND_SPEC.md |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| JDK usado para validar build/boot | JDK 22 local (`~/.jdks/openjdk-22.0.2`), sem alterar `JAVA_HOME` do sistema | `JAVA_HOME` do SO aponta para JDK 18; pom exige Java 21; usuário escolheu essa opção (AD-002) | y |
| Versionamento | Git inicializado neste diretório, um commit por tarefa | Usuário escolheu essa opção (AD-001) | y |
| Código-fonte segue exatamente os snippets do BACKEND_SPEC.md §2.1 e §2.3 | Sim, copiado literalmente (entry point + 3 SQL migrations) | O spec já define o conteúdo exato esperado; não há ambiguidade a resolver | y |

**Open questions:** nenhuma.

---

## User Stories

### P1: Aplicação sobe com schema de banco pronto ⭐ MVP

**User Story**: Como desenvolvedor do backend, quero que a aplicação Spring Boot suba corretamente com o schema de banco já criado, para poder começar a implementar os pacotes de domínio (auth, event, photo) nas fases seguintes.

**Why P1**: É a fundação — nenhuma fase seguinte é possível sem isso.

**Acceptance Criteria**:

1. WHEN a aplicação é iniciada (`mvn spring-boot:run` ou run da classe `EternizaApplication`) THEN o sistema SHALL logar `Started EternizaApplication` no console, sem exceptions fatais
2. WHEN a aplicação inicia pela primeira vez com o Postgres da Fase 1 disponível THEN o Flyway SHALL executar `V1__create_hosts.sql`, `V2__create_events.sql` e `V3__create_photos.sql` em ordem, criando as tabelas `hosts`, `events`, `photos` e os tipos `event_status`, `film_style`, `photo_status`
3. WHEN a aplicação é reiniciada com as migrations já aplicadas THEN o Flyway SHALL detectar que não há scripts novos e não SHALL falhar nem reaplicar migrations existentes

**Independent Test**: Subir a aplicação, checar o log de boot, e consultar o Postgres (`\dt`) confirmando a existência das 3 tabelas com as colunas/constraints definidas no BACKEND_SPEC.md §2.3.

---

## Edge Cases

- WHEN o Docker da Fase 1 não está rodando THEN a aplicação SHALL falhar ao iniciar com erro de conexão de banco (comportamento padrão do Spring/HikariCP — nenhum tratamento especial necessário nesta fase)
- WHEN a aplicação já foi iniciada antes (schema já existe) THEN o Flyway SHALL ser idempotente (não reexecuta migrations já aplicadas)

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| CFG-01 | P1: Entry point sobe sem erro | Implementing | Pending |
| CFG-02 | P1: Migrations criam schema completo | Implementing | Pending |
| CFG-03 | P1: Flyway idempotente em restart | Implementing | Pending |

**Coverage:** 3 total, 3 mapeados para execução, 0 sem mapeamento

---

## Success Criteria

- [ ] Console mostra `Started EternizaApplication in X.XXX seconds`
- [ ] Tabelas `hosts`, `events`, `photos` existem no Postgres com as colunas do BACKEND_SPEC.md §2.3
- [ ] Reiniciar a aplicação não gera erro de migration
