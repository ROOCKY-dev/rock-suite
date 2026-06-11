package dev.rock.api.event;

/**
 * Five-level listener ordering (TRS §8). {@link #FIRST} listeners receive the
 * event before all others; {@link #LAST} listeners observe all mutations made
 * by earlier listeners. Default is {@link #NORMAL}.
 */
public enum EventPriority {
    FIRST,
    EARLY,
    NORMAL,
    LATE,
    LAST
}
