package dev.rock.api.events.world;

import dev.rock.api.event.AbstractCancellable;
import java.util.Objects;
import java.util.UUID;

/**
 * Pre-action block mutation event (platform keystone K1). Published
 * synchronously on the tick thread by loader adapters BEFORE the change is
 * applied; cancelling prevents the change. Claims protection cancels at
 * EARLY; logging records at LAST (and never sees cancelled changes).
 *
 * <p>Modded servers are full of machine-controlled fake players —
 * {@code fakePlayer} lets protection policy treat them separately.
 *
 * @param actor null when no player caused the change (mobs, environment)
 */
public final class BlockChangeEvent extends AbstractCancellable {

    private final UUID actor;
    private final boolean fakePlayer;
    private final UUID worldId;
    private final int x;
    private final int y;
    private final int z;
    private final BlockChangeType type;
    private final String blockBefore;
    private final String blockAfter;

    public BlockChangeEvent(UUID actor, boolean fakePlayer, UUID worldId, int x, int y, int z,
            BlockChangeType type, String blockBefore, String blockAfter) {
        this.actor = actor;
        this.fakePlayer = fakePlayer;
        this.worldId = Objects.requireNonNull(worldId, "worldId");
        this.x = x;
        this.y = y;
        this.z = z;
        this.type = Objects.requireNonNull(type, "type");
        this.blockBefore = Objects.requireNonNull(blockBefore, "blockBefore");
        this.blockAfter = Objects.requireNonNull(blockAfter, "blockAfter");
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

    public BlockChangeType type() {
        return type;
    }

    /** Registry id of the block before the change, e.g. {@code minecraft:stone}. */
    public String blockBefore() {
        return blockBefore;
    }

    /** Registry id after the change ({@code minecraft:air} for a break). */
    public String blockAfter() {
        return blockAfter;
    }
}
