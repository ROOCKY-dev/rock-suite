CREATE TABLE rock_accounts (
    id           VARCHAR(36)   PRIMARY KEY,
    owner_ref    VARCHAR(64)   NOT NULL UNIQUE,
    account_type VARCHAR(16)   NOT NULL,
    balance      NUMERIC(20,4) NOT NULL,
    deleted_at   BIGINT
);

CREATE TABLE rock_transactions (
    id             VARCHAR(36)   PRIMARY KEY,
    source_account VARCHAR(64)   NOT NULL,
    target_account VARCHAR(64)   NOT NULL,
    amount         NUMERIC(20,4) NOT NULL,
    status         VARCHAR(16)   NOT NULL,
    reversal_of    VARCHAR(36),
    ts             BIGINT        NOT NULL,
    reason         VARCHAR(255)  NOT NULL
);

CREATE INDEX idx_rock_tx_source ON rock_transactions (source_account);
CREATE INDEX idx_rock_tx_target ON rock_transactions (target_account);
