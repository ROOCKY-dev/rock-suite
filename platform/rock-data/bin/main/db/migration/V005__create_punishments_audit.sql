CREATE TABLE rock_punishments (
    id              VARCHAR(36)  PRIMARY KEY,
    punishment_type VARCHAR(16)  NOT NULL,
    target          VARCHAR(36)  NOT NULL,
    issuer_ref      VARCHAR(64)  NOT NULL,
    reason          VARCHAR(255) NOT NULL,
    created         BIGINT       NOT NULL,
    expires         BIGINT,
    revoked_at      BIGINT,
    revoked_by      VARCHAR(36)
);

CREATE INDEX idx_rock_punishments_target ON rock_punishments (target);

CREATE TABLE rock_audit (
    id          VARCHAR(36)  PRIMARY KEY,
    ts          BIGINT       NOT NULL,
    actor_ref   VARCHAR(64)  NOT NULL,
    action      VARCHAR(32)  NOT NULL,
    target_type VARCHAR(32)  NOT NULL,
    target_id   VARCHAR(36)  NOT NULL,
    details     TEXT         NOT NULL
);

CREATE INDEX idx_rock_audit_target ON rock_audit (target_type, target_id);
CREATE INDEX idx_rock_audit_ts ON rock_audit (ts);
