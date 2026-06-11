package dev.rock.api.domain;

/** Player account states (DMS). */
public enum PlayerStatus {
    ACTIVE,
    BANNED,
    SUSPENDED,
    /** GDPR erasure — player data anonymised (TRS §22). */
    DELETED
}
