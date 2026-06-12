-- Contexts + temporary permissions: rebuild permission tables with a context
-- discriminator in the primary key and a nullable expiry (portable
-- create/copy/drop/rename dance — SQLite cannot alter primary keys).

CREATE TABLE rock_player_permissions_v2 (
    player_id VARCHAR(36)  NOT NULL,
    node      VARCHAR(255) NOT NULL,
    context   VARCHAR(255) NOT NULL DEFAULT '',
    state     VARCHAR(8)   NOT NULL,
    expires   BIGINT,
    PRIMARY KEY (player_id, node, context)
);
INSERT INTO rock_player_permissions_v2 (player_id, node, context, state)
    SELECT player_id, node, '', state FROM rock_player_permissions;
DROP TABLE rock_player_permissions;
ALTER TABLE rock_player_permissions_v2 RENAME TO rock_player_permissions;

CREATE TABLE rock_group_permissions_v2 (
    group_id VARCHAR(36)  NOT NULL,
    node     VARCHAR(255) NOT NULL,
    context  VARCHAR(255) NOT NULL DEFAULT '',
    state    VARCHAR(8)   NOT NULL,
    expires  BIGINT,
    PRIMARY KEY (group_id, node, context)
);
INSERT INTO rock_group_permissions_v2 (group_id, node, context, state)
    SELECT group_id, node, '', state FROM rock_group_permissions;
DROP TABLE rock_group_permissions;
ALTER TABLE rock_group_permissions_v2 RENAME TO rock_group_permissions;

-- Typed options / meta (prefix, suffix, weight, module limits)
CREATE TABLE rock_player_options (
    player_id VARCHAR(36)  NOT NULL,
    opt_key   VARCHAR(128) NOT NULL,
    opt_value VARCHAR(255) NOT NULL,
    PRIMARY KEY (player_id, opt_key)
);

CREATE TABLE rock_group_options (
    group_id  VARCHAR(36)  NOT NULL,
    opt_key   VARCHAR(128) NOT NULL,
    opt_value VARCHAR(255) NOT NULL,
    PRIMARY KEY (group_id, opt_key)
);
