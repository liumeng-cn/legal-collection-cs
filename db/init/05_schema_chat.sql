\c chat

CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE conversation (
    id         BIGSERIAL PRIMARY KEY,
    user_id    VARCHAR(64)  NOT NULL,
    role       VARCHAR(32)  NOT NULL,
    title      VARCHAR(255),
    created_at TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE message (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT      NOT NULL,
    role            VARCHAR(32) NOT NULL,
    content         TEXT        NOT NULL,
    created_at      TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_message_conversation ON message (conversation_id);
CREATE INDEX idx_conversation_user ON conversation (user_id);

CREATE TABLE user_memory (
    id          BIGSERIAL PRIMARY KEY,
    user_id     VARCHAR(64) NOT NULL,
    memory_type VARCHAR(16) NOT NULL,
    content     TEXT        NOT NULL,
    embedding   vector(1024),
    importance  INT         NOT NULL DEFAULT 1,
    status      VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at  TIMESTAMP   NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP   NOT NULL DEFAULT now()
);

CREATE INDEX idx_user_memory_user_type_status ON user_memory (user_id, memory_type, status);
CREATE INDEX idx_user_memory_embedding ON user_memory USING hnsw (embedding vector_cosine_ops);
