# Project State — Eterniza Backend

## Decisions

| ID | Decision | Rationale | Date |
| --- | --- | --- | --- |
| AD-001 | Repositório git inicializado neste diretório para permitir commits atômicos por tarefa (tlc-spec-driven) | Diretório não era um repo git; skill exige um commit por tarefa | 2026-07-13 |
| AD-002 | Build/boot da aplicação validado usando JDK 22 (`~/.jdks/openjdk-22.0.2`) via `--release 21`, sem alterar `JAVA_HOME` do sistema | `JAVA_HOME` do sistema aponta para JDK 18; pom.xml exige Java 21; JDK 22 suporta `--release 21` | 2026-07-13 |

## Handoff

_Nenhum trabalho em andamento — ver commits para o progresso concluído._
