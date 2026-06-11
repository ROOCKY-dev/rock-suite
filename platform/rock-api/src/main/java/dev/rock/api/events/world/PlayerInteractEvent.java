package dev.rock.api.events.world;

import dev.rock.api.event.AbstractCancellable;
import java.util.Objects;
import java.util.UUID;

/**
 * Pre-action interaction event (platform keystone K1). Cancellable on the
 * tick thread before the interaction resolves.
 */
public final class PlayerInteractEvent extends AbstractCancellable {

    private final UUID actor;
    private final boolean fakePlayer;
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private final InteractionType type;
    private final String targetId;

    public PlayerInteractEvent(UUID actor, boolean fakePlayer, UUID worldId, int x, int y, int z,
            InteractionType type, String targetId) {
        this.actor = Objects.requireNonNull(actor, "actor");
        this.fakePlayer = fakePlayer;
        this.worldId = Objects.requireNonNull(worldId, "worldId");
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = Objects.requireNonNull(type, "type");
        this.targetId = Objects.requireNonNull(targetId, "targetId");
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

    public InteractionType type() {
        return type;
    }

    /** Registry id of the interaction target (block or entity type). */
    public String targetId() {
        return targetId;
    }
}
