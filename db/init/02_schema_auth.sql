\c auth

CREATE TABLE staff (
    id            BIGSERIAL PRIMARY KEY,
    username      VARCHAR(64)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    name          VARCHAR(64)  NOT NULL,
    role          VARCHAR(32)  NOT NULL DEFAULT 'STAFF',
    created_at    TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE debtor (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(64)  NOT NULL,
    id_card    VARCHAR(18)  NOT NULL UNIQUE,
    phone      VARCHAR(20),
    created_at TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE TABLE debtor_case_binding (
    id         BIGSERIAL PRIMARY KEY,
    debtor_id  BIGINT    NOT NULL,
    case_id    BIGINT    NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_binding_debtor ON debtor_case_binding (debtor_id);
CREATE INDEX idx_binding_case ON debtor_case_binding (case_id);
