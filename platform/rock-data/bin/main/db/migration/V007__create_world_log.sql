CREATE TABLE rock_world_log (
    id           VARCHAR(36) PRIMARY KEY,
    actor        VARCHAR(36),
    fake_actor   INTEGER     NOT NULL DEFAULT 0,
    world_id     VARCHAR(36) NOT NULL,
    x            INTEGER     NOT NULL,
    y            INTEGER     NOT NULL,
    z            INTEGER     NOT NULL,
    action       VARCHAR(16) NOT NULL,
    block_before VARCHAR(255) NOT NULL,
    block_after  VARCHAR(255) NOT NULL,
    ts           BIGINT      NOT NULL,
    rolled_back  INTEGER     NOT NULL DEFAULT 0
);

CREATE INDEX idx_rock_world_log_pos ON rock_world_log (world_id, x, z, y);
CREATE INDEX idx_rock_world_log_ts ON rock_world_log (ts);
CREATE INDEX idx_rock_world_log_actor ON rock_world_log (actor);
