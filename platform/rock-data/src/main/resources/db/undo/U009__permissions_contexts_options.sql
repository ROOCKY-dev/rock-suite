DROP TABLE rock_group_options;
DROP TABLE rock_player_options;
CREATE TABLE rock_player_permissions_v1 (
    player_id VARCHAR(36)  NOT NULL,
    node      VARCHAR(255) NOT NULL,
    state     VARCHAR(8)   NOT NULL,
    PRIMARY KEY (player_id, node)
);
INSERT INTO rock_player_permissions_v1 (player_id, node, state)
    SELECT player_id, node, state FROM rock_player_permissions WHERE context = '';
DROP TABLE rock_player_permissions;
ALTER TABLE rock_player_permissions_v1 RENAME TO rock_player_permissions;
CREATE TABLE rock_group_permissions_v1 (
    group_id VARCHAR(36)  NOT NULL,
    node     VARCHAR(255) NOT NULL,
    state    VARCHAR(8)   NOT NULL,
    PRIMARY KEY (group_id, node)
);
INSERT INTO rock_group_permissions_v1 (group_id, node, state)
    SELECT group_id, node, state FROM rock_group_permissions WHERE context = '';
DROP TABLE rock_group_permissions;
ALTER TABLE rock_group_permissions_v1 RENAME TO rock_group_permissions;
