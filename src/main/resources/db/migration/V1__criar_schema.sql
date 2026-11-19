-- =====================================================================
-- Repositório de TCCs e Artigos  |  MySQL 8+ (compatível com AWS RDS)
-- =====================================================================

CREATE TABLE curso (
    id    BIGINT       AUTO_INCREMENT PRIMARY KEY,
    nome  VARCHAR(150) NOT NULL,
    sigla VARCHAR(20),
    CONSTRAINT uk_curso_nome UNIQUE (nome)
);

CREATE TABLE orientador (
    id        BIGINT       AUTO_INCREMENT PRIMARY KEY,
    nome      VARCHAR(150) NOT NULL,
    email     VARCHAR(150) NOT NULL,
    titulacao VARCHAR(50),
    ativo     BOOLEAN      NOT NULL DEFAULT TRUE,
    CONSTRAINT uk_orientador_email UNIQUE (email)
);

CREATE TABLE usuario (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    nome       VARCHAR(150) NOT NULL,
    email      VARCHAR(150) NOT NULL,
    senha_hash VARCHAR(100) NOT NULL,
    perfil     VARCHAR(20)  NOT NULL,   -- ADMIN | BIBLIOTECARIO | PROFESSOR | ALUNO
    ativo      BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em  DATETIME(6)  NOT NULL,
    CONSTRAINT uk_usuario_email UNIQUE (email),
    CONSTRAINT ck_usuario_perfil CHECK (perfil IN ('ADMIN', 'BIBLIOTECARIO', 'PROFESSOR', 'ALUNO'))
);

CREATE TABLE trabalho (
    id                    BIGINT       AUTO_INCREMENT PRIMARY KEY,
    titulo                VARCHAR(300) NOT NULL,
    resumo                TEXT,
    palavras_chave        VARCHAR(500),
    tipo                  VARCHAR(20)  NOT NULL,  -- TCC | ARTIGO | MONOGRAFIA | DISSERTACAO | TESE
    ano                   INT          NOT NULL,
    status                VARCHAR(20)  NOT NULL,  -- PENDENTE | PUBLICADO | ARQUIVADO

    curso_id              BIGINT       NOT NULL,
    orientador_id         BIGINT       NOT NULL,
    enviado_por_id        BIGINT,

    -- metadados do PDF (o binário fica no bucket AWS S3 ou disco local)
    arquivo_nome_original VARCHAR(255) NOT NULL,
    arquivo_chave         VARCHAR(500) NOT NULL,
    arquivo_content_type  VARCHAR(100) NOT NULL,
    arquivo_tamanho_bytes BIGINT       NOT NULL,
    arquivo_hash_sha256   VARCHAR(64)  NOT NULL,

    total_downloads       BIGINT       NOT NULL DEFAULT 0,
    criado_em             DATETIME(6)  NOT NULL,
    atualizado_em         DATETIME(6)  NOT NULL,

    CONSTRAINT fk_trabalho_curso       FOREIGN KEY (curso_id)       REFERENCES curso (id),
    CONSTRAINT fk_trabalho_orientador  FOREIGN KEY (orientador_id)  REFERENCES orientador (id),
    CONSTRAINT fk_trabalho_enviado_por FOREIGN KEY (enviado_por_id) REFERENCES usuario (id) ON DELETE SET NULL,
    CONSTRAINT uk_trabalho_arquivo_chave UNIQUE (arquivo_chave),
    CONSTRAINT uk_trabalho_arquivo_hash  UNIQUE (arquivo_hash_sha256),
    CONSTRAINT ck_trabalho_ano    CHECK (ano BETWEEN 1900 AND 2100),
    CONSTRAINT ck_trabalho_tipo   CHECK (tipo IN ('TCC', 'ARTIGO', 'MONOGRAFIA', 'DISSERTACAO', 'TESE')),
    CONSTRAINT ck_trabalho_status CHECK (status IN ('PENDENTE', 'PUBLICADO', 'ARQUIVADO'))
);

CREATE INDEX idx_trabalho_curso      ON trabalho (curso_id);
CREATE INDEX idx_trabalho_orientador ON trabalho (orientador_id);
CREATE INDEX idx_trabalho_ano        ON trabalho (ano);
CREATE INDEX idx_trabalho_status     ON trabalho (status);
CREATE INDEX idx_trabalho_tipo       ON trabalho (tipo);

-- Um trabalho pode ter vários autores
CREATE TABLE trabalho_autor (
    trabalho_id BIGINT       NOT NULL,
    nome_autor  VARCHAR(150) NOT NULL,
    PRIMARY KEY (trabalho_id, nome_autor),
    CONSTRAINT fk_trabalho_autor_trabalho FOREIGN KEY (trabalho_id) REFERENCES trabalho (id) ON DELETE CASCADE
);

CREATE INDEX idx_trabalho_autor_nome ON trabalho_autor (nome_autor);

-- Auditoria de downloads
CREATE TABLE download_log (
    id          BIGINT      AUTO_INCREMENT PRIMARY KEY,
    trabalho_id BIGINT      NOT NULL,
    usuario_id  BIGINT,
    data_hora   DATETIME(6) NOT NULL,
    ip_origem   VARCHAR(45),
    CONSTRAINT fk_download_log_trabalho FOREIGN KEY (trabalho_id) REFERENCES trabalho (id) ON DELETE CASCADE,
    CONSTRAINT fk_download_log_usuario  FOREIGN KEY (usuario_id)  REFERENCES usuario (id)  ON DELETE SET NULL
);

CREATE INDEX idx_download_log_trabalho ON download_log (trabalho_id);

-- ---------------------------------------------------------------------
-- OPCIONAL (acervos grandes no MySQL InnoDB): busca full-text nativa.
-- Descomente e consulte com MATCH(titulo, resumo, palavras_chave) AGAINST(...)
-- ---------------------------------------------------------------------
-- CREATE FULLTEXT INDEX idx_trabalho_busca ON trabalho (titulo, resumo, palavras_chave);
