CREATE TABLE rock_teams (
    id         VARCHAR(36) PRIMARY KEY,
    name       VARCHAR(64) NOT NULL,
    created    BIGINT      NOT NULL,
    deleted_at BIGINT
);

-- Names are unique among ACTIVE teams only; soft-deleted rows keep their
-- name for audit without blocking reuse (partial index: SQLite + PostgreSQL).
CREATE UNIQUE INDEX idx_rock_teams_active_name ON rock_teams (name) WHERE deleted_at IS NULL;

CREATE TABLE rock_team_members (
    team_id   VARCHAR(36) NOT NULL,
    player_id VARCHAR(36) NOT NULL UNIQUE,
    role      VARCHAR(16) NOT NULL,
    PRIMARY KEY (team_id, player_id)
);

CREATE INDEX idx_rock_team_members_player ON rock_team_members (player_id);
