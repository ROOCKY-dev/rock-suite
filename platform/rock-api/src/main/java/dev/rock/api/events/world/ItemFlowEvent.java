package dev.rock.api.events.world;

import dev.rock.api.event.AbstractCancellable;
import java.util.Objects;
import java.util.UUID;

/**
 * Pre-action container item movement (K1 family — Ledger's container-theft
 * tracking surface). Cancellable for fine-grained protection; rock-logging
 * records approved flows at LAST priority.
 */
public final class ItemFlowEvent extends AbstractCancellable {

    private final UUID actor;
    private final boolean fakePlayer;
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private final ItemFlowDirection direction;
    private final String itemId;
    private final int count;

    public ItemFlowEvent(UUID actor, boolean fakePlayer, UUID worldId, int x, int y, int z,
            ItemFlowDirection direction, String itemId, int count) {
        this.actor = actor;
        this.fakePlayer = fakePlayer;
        this.worldId = Objects.requireNonNull(worldId, "worldId");
        this.x = x;
        this.y = y;
        this.z = z;
        this.direction = Objects.requireNonNull(direction, "direction");
        this.itemId = Objects.requireNonNull(itemId, "itemId");
        if (count <= 0) {
            throw new IllegalArgumentException("count must be positive");
        }
        this.count = count;
    }

    public UUID actor() {
        return actor;
    }

    public boolean fakePlayer() {
        return fakePlayer;
    }

    public UUID worldId() {
        return worldId;
    }

    public int x() {
        return x;
    }

    public int y() {
        return y;
    }

    public int z() {
        return z;
    }

    public ItemFlowDirection direction() {
        return direction;
    }

    /** Registry id of the moved item, e.g. {@code minecraft:diamond}. */
    public String itemId() {
        return itemId;
    }

    public int count() {
        return count;
    }
}
