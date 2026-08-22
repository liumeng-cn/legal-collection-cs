\c business

CREATE TABLE case_info (
    id           BIGSERIAL PRIMARY KEY,
    case_no      VARCHAR(64)   NOT NULL UNIQUE,
    debtor_id    BIGINT        NOT NULL,
    status       VARCHAR(32)   NOT NULL,
    amount_total NUMERIC(18,2) NOT NULL,
    created_at   TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TABLE debt_detail (
    id         BIGSERIAL PRIMARY KEY,
    case_id    BIGINT        NOT NULL,
    principal  NUMERIC(18,2) NOT NULL,
    interest   NUMERIC(18,2) NOT NULL DEFAULT 0,
    fee        NUMERIC(18,2) NOT NULL DEFAULT 0,
    updated_at TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE TABLE repayment_record (
    id        BIGSERIAL PRIMARY KEY,
    case_id   BIGINT        NOT NULL,
    amount    NUMERIC(18,2) NOT NULL,
    channel   VARCHAR(32),
    repaid_at TIMESTAMP     NOT NULL DEFAULT now()
);

CREATE INDEX idx_case_debtor ON case_info (debtor_id);
CREATE INDEX idx_debt_detail_case ON debt_detail (case_id);
CREATE INDEX idx_repayment_case ON repayment_record (case_id);
