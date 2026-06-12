package dev.rock.api.services;

import dev.rock.api.domain.RockTeam;
import dev.rock.api.domain.TeamRole;
import dev.rock.api.service.RockService;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Team identity contract. Other modules consume team membership through this
 * service — e.g. rock-claims maps team roles onto claim trust for
 * {@code GroupOwner}-owned claims.
 *
 * <p>Cached reads ({@link #teamOfCached}, {@link #roleOfCached}) are
 * tick-thread safe.
 */
public interface TeamService extends RockService {

    /** Creates a team with the founding player as LEADER. */
    CompletableFuture<RockTeam> create(String name, UUID leader);

    CompletableFuture<Optional<RockTeam>> findByName(String name);

    CompletableFuture<Optional<RockTeam>> findById(UUID teamId);

    /** Adds or updates a member. A player may belong to one team at a time. */
    CompletableFuture<Void> addMember(UUID teamId, UUID playerId, TeamRole role);

    CompletableFuture<Void> removeMember(UUID teamId, UUID playerId);

    CompletableFuture<Map<UUID, TeamRole>> membersOf(UUID teamId);

    /** Soft-deletes the team and clears its membership. */
    CompletableFuture<Void> disband(UUID teamId);

    /** Cache-backed team lookup for a player; tick-thread safe. */
    Optional<RockTeam> teamOfCached(UUID playerId);

    /** Cache-backed role of a player within a team; tick-thread safe. */
    Optional<TeamRole> roleOfCached(UUID teamId, UUID playerId);
}
