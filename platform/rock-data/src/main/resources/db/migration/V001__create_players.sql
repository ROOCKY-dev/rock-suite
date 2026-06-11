CREATE TABLE rock_players (
    id               VARCHAR(36)  PRIMARY KEY,
    username         VARCHAR(48)  NOT NULL,
    preferred_locale VARCHAR(35)  NOT NULL,
    first_join       BIGINT       NOT NULL,
    last_seen        BIGINT       NOT NULL,
    status           VARCHAR(16)  NOT NULL,
    deleted_at       BIGINT
);

CREATE INDEX idx_rock_players_username ON rock_players (username);
