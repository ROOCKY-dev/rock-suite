CREATE TABLE rock_claim_members (
    claim_id  VARCHAR(36) NOT NULL,
    player_id VARCHAR(36) NOT NULL,
    role      VARCHAR(16) NOT NULL,
    PRIMARY KEY (claim_id, player_id)
);

CREATE INDEX idx_rock_claim_members_player ON rock_claim_members (player_id);
