package dev.rock.api.domain;

/**
 * Per-claim trust levels, lowest to highest (GriefPrevention's proven model).
 * Each level implies the ones below it.
 */
public enum ClaimRole {
    /** Use doors, buttons, pressure plates. */
    ACCESS,
    /** Open chests and other inventories. */
    CONTAINER,
    /** Break and place blocks. */
    BUILD,
    /** Manage members and claim settings. */
    MANAGER;

    public boolean atLeast(ClaimRole required) {
        return ordinal() >= required.ordinal();
    }
}
