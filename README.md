# Repositório de TCCs e Artigos

Spring Boot 3 · Spring Data JPA · PostgreSQL + Flyway · armazenamento local ou AWS S3.

## Modelo de dados

```mermaid
erDiagram
    CURSO ||--o{ TRABALHO : "possui"
    ORIENTADOR ||--o{ TRABALHO : "orienta"
    USUARIO ||--o{ TRABALHO : "envia"
    TRABALHO ||--|{ TRABALHO_AUTOR : "tem autores"
    TRABALHO ||--o{ DOWNLOAD_LOG : "registra"
    USUARIO ||--o{ DOWNLOAD_LOG : "realiza"

    CURSO { bigint id PK  varchar nome UK  varchar sigla }
    ORIENTADOR { bigint id PK  varchar nome  varchar email UK  varchar titulacao  boolean ativo }
    USUARIO { bigint id PK  varchar nome  varchar email UK  varchar senha_hash  varchar perfil  boolean ativo }
    TRABALHO {
        bigint id PK
        varchar titulo
        text resumo
        varchar palavras_chave
        varchar tipo
        int ano
        varchar status
        bigint curso_id FK
        bigint orientador_id FK
        bigint enviado_por_id FK
        varchar arquivo_chave UK
        varchar arquivo_hash_sha256 UK
        bigint total_downloads
    }
    TRABALHO_AUTOR { bigint trabalho_id PK,FK  varchar nome_autor PK }
    DOWNLOAD_LOG { bigint id PK  bigint trabalho_id FK  bigint usuario_id FK  timestamp data_hora  varchar ip_origem }
```

## Estrutura

```
model/entity     Curso, Orientador, Usuario, Trabalho, ArquivoInfo, DownloadLog
model/enums      TipoTrabalho, StatusTrabalho, Perfil
repository       Spring Data + spec/TrabalhoSpecs (busca dinâmica)
service          CursoService, OrientadorService, TrabalhoService
storage          StorageService → LocalStorageService | S3StorageService
dto              requests/responses
```

## Como funciona

- **Upload**: valida a assinatura `%PDF-`, calcula SHA-256 (bloqueia duplicados), grava no storage em
  `trabalhos/{ano}/{cursoId}/{uuid}.pdf` e salva os metadados. Se o banco der rollback, o arquivo é removido.
- **Moderação**: ALUNO/PROFESSOR enviam como `PENDENTE`; ADMIN/BIBLIOTECARIO publicam direto ou aprovam depois.
  A busca pública só enxerga `PUBLICADO`.
- **Busca**: filtros por curso, ano, orientador e tipo + termo livre (cada palavra deve aparecer em título, resumo,
  palavras-chave ou autor). Para acervos grandes, ative o bloco `tsvector`/GIN comentado em `V1__criar_schema.sql`.
- **Troca de storage**: `app.storage.tipo=local` ou `s3` no `application.yml`.
