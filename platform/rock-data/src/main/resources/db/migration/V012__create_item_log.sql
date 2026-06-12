CREATE TABLE rock_item_log (
    id         VARCHAR(36)  PRIMARY KEY,
    actor      VARCHAR(36),
    fake_actor INTEGER      NOT NULL DEFAULT 0,
    world_id   VARCHAR(36)  NOT NULL,
    x          INTEGER      NOT NULL,
    y          INTEGER      NOT NULL,
    z          INTEGER      NOT NULL,
    direction  VARCHAR(8)   NOT NULL,
    item_id    VARCHAR(255) NOT NULL,
    item_count INTEGER      NOT NULL,
    ts         BIGINT       NOT NULL
);

CREATE INDEX idx_rock_item_log_pos ON rock_item_log (world_id, x, z, y);
CREATE INDEX idx_rock_item_log_actor ON rock_item_log (actor);
CREATE INDEX idx_rock_item_log_ts ON rock_item_log (ts);
