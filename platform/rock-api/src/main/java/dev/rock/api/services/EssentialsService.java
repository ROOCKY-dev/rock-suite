package dev.rock.api.services;

import dev.rock.api.domain.RockLocation;
import dev.rock.api.service.RockService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Admin/player quality-of-life kit (FTB Essentials / EssentialCommands
 * territory): homes, warps, teleport requests. Home limits come from the
 * permission option {@code rock.essentials.homes.max} (typed values — one
 * limit system for the whole platform).
 */
public interface EssentialsService extends RockService {

    /** Permission option key controlling the per-player home limit. */
    String HOME_LIMIT_OPTION = "rock.essentials.homes.max";

    // --- Homes ---------------------------------------------------------------

    /** Fails with {@code IllegalStateException} when the player's home limit is reached. */
    CompletableFuture<Void> setHome(UUID playerId, String name, RockLocation location);

    CompletableFuture<Optional<RockLocation>> home(UUID playerId, String name);

    CompletableFuture<Void> deleteHome(UUID playerId, String name);

    CompletableFuture<List<String>> homes(UUID playerId);

    // --- Warps ---------------------------------------------------------------

    CompletableFuture<Void> setWarp(String name, RockLocation location, UUID createdBy);

    CompletableFuture<Optional<RockLocation>> warp(String name);

    CompletableFuture<Void> deleteWarp(String name);

    CompletableFuture<List<String>> warps();

    // --- Teleport requests (TPA) ----------------------------------------------

    /** Creates a teleport request from requester to target; expires after a configured window. */
    void tpa(UUID requester, UUID target);

    /**
     * Accepts the pending request to this target, returning the requester id
     * when one existed and was not expired. Teleportation itself runs through
     * the loader-provided {@link dev.rock.api.world.PlayerTeleporter} when present.
     */
    Optional<UUID> tpaccept(UUID target);

    Optional<UUID> tpdeny(UUID target);
}
