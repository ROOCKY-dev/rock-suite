package dev.rock.api.domain;

import dev.rock.api.events.world.ItemFlowDirection;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** One recorded container item movement (rock-logging P2 — theft tracking). */
public record RockItemLogEntry(
        UUID id,
        UUID actor,
        boolean fakeActor,
        UUID worldId,
        int x,
        int y,
        int z,
        ItemFlowDirection direction,
        String itemId,
        int count,
        Instant timestamp) {

    public RockItemLogEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(worldId, "worldId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(itemId, "itemId");
        Objects.requireNonNull(timestamp, "timestamp");
    }
}
