package dev.rock.api.domain;

import dev.rock.api.events.world.BlockChangeType;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One recorded world change (rock-logging). Immutable except for the
 * {@code rolledBack} flag, which flips when a rollback/restore touches it.
 *
 * @param actor null when no player caused the change
 */
public record RockWorldLogEntry(
        UUID id,
        UUID actor,
        boolean fakeActor,
        UUID worldId,
        int x,
        int y,
        int z,
        BlockChangeType action,
        String blockBefore,
        String blockAfter,
        Instant timestamp,
        boolean rolledBack) {

    public RockWorldLogEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(blockBefore, "blockBefore");
        Objects.requireNonNull(blockAfter, "blockAfter");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
