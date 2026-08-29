\c knowledge

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE document (
    id            BIGSERIAL PRIMARY KEY,
    title         VARCHAR(255) NOT NULL,
    content       TEXT         NOT NULL,
    allowed_roles TEXT[],
    case_id       BIGINT,
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE document_chunk (
    id          BIGSERIAL PRIMARY KEY,
    document_id BIGINT NOT NULL,
    chunk_index INT    NOT NULL DEFAULT 0,
    chunk_text  TEXT   NOT NULL,
    embedding   vector(1024)
);

CREATE INDEX idx_chunk_document ON document_chunk (document_id);
