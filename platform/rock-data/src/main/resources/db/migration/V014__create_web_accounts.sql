-- Web dashboard login accounts (TRS §12 User Accounts). Passwords are stored
-- as Argon2id hashes (TRS §9) — never plaintext. A web account may be linked
-- to a RockPlayer (player_id) or stand alone (a pure-admin operator).
CREATE TABLE rock_web_accounts (
    id            VARCHAR(36)  PRIMARY KEY,
    username      VARCHAR(48)  NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(16)  NOT NULL,
    player_id     VARCHAR(36),
    created       BIGINT       NOT NULL,
    last_login    BIGINT
);
