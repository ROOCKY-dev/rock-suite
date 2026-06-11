package dev.rock.core.loader;

import dev.rock.api.event.EventBus;
import dev.rock.api.events.world.BlockChangeEvent;
import dev.rock.api.events.world.BlockChangeType;
import dev.rock.api.events.world.InteractionType;
import dev.rock.api.events.world.PlayerInteractEvent;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loader-agnostic world-interaction bridge (platform keystone K1). Adapters
 * feed raw values from their pre-action hooks; the bridge publishes the
 * cancellable platform event synchronously (tick thread, in-memory listeners
 * only) and reports whether the action is allowed.
 *
 * <p>Also owns the loader-world → stable UUID mapping so claims and logs keyed
 * by world UUID survive across the adapter boundary.
 */
public final class WorldEventBridge {

    private final EventBus eventBus;
    private final Map<String, UUID> worldIds = new ConcurrentHashMap<>();

    public WorldEventBridge(EventBus eventBus) {
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    /** Stable UUID for a loader world key (e.g. dimension id "minecraft:overworld"). */
    public UUID worldId(String worldKey) {
        return worldIds.computeIfAbsent(worldKey, key -> UUID.nameUUIDFromBytes(("rock-world:" + key).getBytes()));
    }

    /**
     * Publishes a pre-action block change.
     *
     * @return true when the change may proceed; false when a listener cancelled it
     */
    public boolean blockChange(UUID actor, boolean fakePlayer, UUID worldId, int x, int y, int z,
            BlockChangeType type, String blockBefore, String blockAfter) {
        BlockChangeEvent event = eventBus.publish(
                new BlockChangeEvent(actor, fakePlayer, worldId, x, y, z, type, blockBefore, blockAfter));
        return !event.cancelled();
    }

    /**
     * Publishes a pre-action interaction.
     *
     * @return true when the interaction may proceed
     */
    public boolean interact(UUID actor, boolean fakePlayer, UUID worldId, int x, int y, int z,
            InteractionType type, String targetId) {
        PlayerInteractEvent event = eventBus.publish(
                new PlayerInteractEvent(actor, fakePlayer, worldId, x, y, z, type, targetId));
        return !event.cancelled();
    }
}
