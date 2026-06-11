package dev.rock.api.domain;

/** Required for economy integrity (DMS). */
public enum TransactionStatus {
    /** Initiated but not yet committed. */
    PENDING,
    /** Successfully applied to both accounts. */
    COMPLETED,
    /** Attempted but did not complete (e.g. insufficient funds). */
    FAILED,
    /** Completed but subsequently reversed; reversalOf is non-null. */
    REVERSED
}
