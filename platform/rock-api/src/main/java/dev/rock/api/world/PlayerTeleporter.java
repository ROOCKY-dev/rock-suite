package dev.rock.api.world;

import dev.rock.api.domain.RockLocation;
import dev.rock.api.service.RockService;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Loader-provided teleport capability (K4 family). Used by rock-essentials
 * (homes, warps, TPA). Implementations hop to the tick thread internally.
 */
public interface PlayerTeleporter extends RockService {

    CompletableFuture<Void> teleport(UUID playerId, RockLocation location);

    /** Current position of an online player. */
    CompletableFuture<RockLocation> locate(UUID playerId);
}
