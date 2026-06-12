CREATE TABLE rock_groups (
    id         VARCHAR(36)  PRIMARY KEY,
    name       VARCHAR(64)  NOT NULL UNIQUE,
    priority   INTEGER      NOT NULL,
    deleted_at BIGINT
);

CREATE TABLE rock_group_permissions (
    group_id VARCHAR(36)  NOT NULL,
    node     VARCHAR(255) NOT NULL,
    state    VARCHAR(8)   NOT NULL,
    PRIMARY KEY (group_id, node)
);

CREATE TABLE rock_player_permissions (
    player_id VARCHAR(36)  NOT NULL,
    node      VARCHAR(255) NOT NULL,
    state     VARCHAR(8)   NOT NULL,
    PRIMARY KEY (player_id, node)
);

CREATE TABLE rock_player_groups (
    player_id VARCHAR(36) NOT NULL,
    group_id  VARCHAR(36) NOT NULL,
    PRIMARY KEY (player_id, group_id)
);
