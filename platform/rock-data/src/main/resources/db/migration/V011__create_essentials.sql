CREATE TABLE rock_homes (
    player_id VARCHAR(36) NOT NULL,
    name      VARCHAR(64) NOT NULL,
    world_id  VARCHAR(36) NOT NULL,
    x         DOUBLE PRECISION NOT NULL,
    y         DOUBLE PRECISION NOT NULL,
    z         DOUBLE PRECISION NOT NULL,
    yaw       REAL        NOT NULL DEFAULT 0,
    pitch     REAL        NOT NULL DEFAULT 0,
    PRIMARY KEY (player_id, name)
);

CREATE TABLE rock_warps (
    name       VARCHAR(64) PRIMARY KEY,
    world_id   VARCHAR(36) NOT NULL,
    x          DOUBLE PRECISION NOT NULL,
    y          DOUBLE PRECISION NOT NULL,
    z          DOUBLE PRECISION NOT NULL,
    yaw        REAL        NOT NULL DEFAULT 0,
    pitch      REAL        NOT NULL DEFAULT 0,
    created_by VARCHAR(36) NOT NULL
);
