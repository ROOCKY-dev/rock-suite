package dev.rock.api.world;

import dev.rock.api.service.RockService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Loader-provided world mutation capability (platform keystone K4). Used by
 * rock-logging rollback/restore and future region tools. Implementations
 * must hop to the tick thread internally; callers may invoke from any thread.
 */
public interface WorldMutator extends RockService {

    /** Sets a block by registry id, e.g. {@code minecraft:stone}. */
    CompletableFuture<Void> setBlock(UUID worldId, int x, int y, int z, String blockId);

    /** Reads the current block registry id at a position. */
    CompletableFuture<String> getBlock(UUID worldId, int x, int y, int z);
}
