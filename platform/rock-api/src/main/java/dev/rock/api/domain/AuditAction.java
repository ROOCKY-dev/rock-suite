package dev.rock.api.domain;

/** Administrative action categories recorded in the audit log (TRS §9). */
public enum AuditAction {
    CREATE,
    UPDATE,
    DELETE,
    TRANSFER,
    GRANT,
    REVOKE,
    BAN,
    UNBAN,
    CONFIG_RELOAD,
    MODULE_ENABLE,
    MODULE_DISABLE,
    /** GDPR right-to-erasure request (TRS §22). */
    ERASURE
}
