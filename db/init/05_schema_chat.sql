\c chat

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
