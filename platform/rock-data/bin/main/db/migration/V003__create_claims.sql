CREATE TABLE rock_claims (
    id           VARCHAR(36)  PRIMARY KEY,
    display_name VARCHAR(128) NOT NULL,
    owner_ref    VARCHAR(64)  NOT NULL,
    claim_type   VARCHAR(16)  NOT NULL,
    world_id     VARCHAR(36)  NOT NULL,
    bounds_type  VARCHAR(16)  NOT NULL,
    bounds_data  TEXT         NOT NULL,
    created      BIGINT       NOT NULL,
    modified     BIGINT       NOT NULL,
    deleted_at   BIGINT
);

CREATE INDEX idx_rock_claims_owner ON rock_claims (owner_ref);
CREATE INDEX idx_rock_claims_world ON rock_claims (world_id);
