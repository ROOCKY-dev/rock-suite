CREATE TABLE rock_discord_links (
    player_id   VARCHAR(36) PRIMARY KEY,
    discord_id  VARCHAR(32) NOT NULL,
    linked_at   BIGINT      NOT NULL,
    unlinked_at BIGINT
);

CREATE INDEX idx_rock_discord_id ON rock_discord_links (discord_id);

CREATE TABLE rock_metadata (
    entity_id     VARCHAR(36)  NOT NULL,
    entity_type   VARCHAR(32)  NOT NULL,
    namespace     VARCHAR(64)  NOT NULL,
    meta_key      VARCHAR(128) NOT NULL,
    meta_value    TEXT         NOT NULL,
    last_modified BIGINT       NOT NULL,
    PRIMARY KEY (entity_id, namespace, meta_key)
);
