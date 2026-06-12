package dev.rock.claims;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.command.CommandResult;
import dev.rock.api.command.CommandService;
import dev.rock.api.command.CommandSpec;
import dev.rock.api.domain.ClaimRole;
import dev.rock.api.domain.ClaimType;
import dev.rock.api.domain.RockClaim;
import dev.rock.api.domain.RockLocation;
import dev.rock.api.domain.bounds.ChunkBounds;
import dev.rock.api.domain.bounds.ChunkBounds.ChunkCoordinate;
import dev.rock.api.domain.owner.PlayerOwner;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.api.services.ClaimService;
import dev.rock.api.services.PlayerService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * /rock claims — the player claim surface:
 * claim [name] (claims the chunk you stand in), info, trust <player> <role>,
 * untrust <player>, abandon.
 */
@RockInternal
@Singleton
public final class ClaimsCommands implements LifecycleAware {

    private final CommandService commands;
    private final ClaimService claims;
    private final ServiceRegistry services;

    @Inject
    public ClaimsCommands(CommandService commands, ClaimService claims, ServiceRegistry services) {
        this.commands = commands;
        this.claims = claims;
        this.services = services;
    }

    private Optional<RockClaim> claimAtSender(dev.rock.api.command.CommandContext ctx) {
        RockLocation location = ctx.sender().location();
        if (location == null) {
            ctx.sender().sendMessage("Only in-game players can use claim commands.");
            return Optional.empty();
        }
        Optional<RockClaim> claim = claims.claimAtCached(
                location.worldId(), (int) location.x(), (int) location.y(), (int) location.z());
        if (claim.isEmpty()) {
            ctx.sender().sendMessage("No claim here.");
        }
        return claim;
    }

    @Override
    public void onEnable() {
        commands.register(new CommandSpec(List.of("claims", "claim"),
                "Claims the chunk you stand in: /rock claims claim [name]", "rock.claims.create", ctx -> {
            RockLocation location = ctx.sender().location();
            UUID playerId = ctx.sender().playerId();
            if (location == null || playerId == null) {
                ctx.sender().sendMessage("Only in-game players can claim.");
                return CommandResult.FAILURE;
            }
            ChunkCoordinate chunk = ChunkCoordinate.ofBlock((int) location.x(), (int) location.z());
            String name = ctx.arg(0, ctx.sender().name() + "'s claim");
            try {
                claims.create(name, new PlayerOwner(playerId), ClaimType.PLAYER,
                        new ChunkBounds(location.worldId(), Set.of(chunk))).join();
                ctx.sender().sendMessage("Claimed chunk " + chunk.chunkX() + "," + chunk.chunkZ()
                        + " as '" + name + "'.");
                return CommandResult.SUCCESS;
            } catch (Exception e) {
                ctx.sender().sendMessage("Could not claim: " + rootMessage(e));
                return CommandResult.FAILURE;
            }
        }));

        commands.register(new CommandSpec(List.of("claims", "info"),
                "Shows the claim you stand in", "rock.claims.info", ctx ->
                claimAtSender(ctx).map(claim -> {
                    ctx.sender().sendMessage("Claim '" + claim.displayName() + "' — owner "
                            + claim.owner().serialize() + ", type " + claim.type());
                    ctx.sender().sendMessage("Members: " + claims.membersOf(claim.id()).join());
                    return CommandResult.SUCCESS;
                }).orElse(CommandResult.USAGE_ERROR)));

        commands.register(new CommandSpec(List.of("claims", "trust"),
                "Trusts a player here: /rock claims trust <player> <ACCESS|CONTAINER|BUILD|MANAGER>",
                "rock.claims.trust", ctx -> {
            if (ctx.args().size() < 2) {
                return CommandResult.USAGE_ERROR;
            }
            return claimAtSender(ctx).map(claim -> {
                if (!isManager(claim, ctx.sender().playerId())) {
                    ctx.sender().sendMessage("Only claim managers can trust players.");
                    return CommandResult.NO_PERMISSION;
                }
                var target = services.find(PlayerService.class)
                        .flatMap(players -> players.findByUsername(ctx.args().get(0)).join());
                if (target.isEmpty()) {
                    ctx.sender().sendMessage("Unknown player: " + ctx.args().get(0));
                    return CommandResult.USAGE_ERROR;
                }
                ClaimRole role;
                try {
                    role = ClaimRole.valueOf(ctx.args().get(1).toUpperCase());
                } catch (IllegalArgumentException e) {
                    ctx.sender().sendMessage("Roles: ACCESS, CONTAINER, BUILD, MANAGER");
                    return CommandResult.USAGE_ERROR;
                }
                claims.trust(claim.id(), target.get().id(), role).join();
                ctx.sender().sendMessage(target.get().username() + " trusted as " + role + ".");
                return CommandResult.SUCCESS;
            }).orElse(CommandResult.USAGE_ERROR);
        }));

        commands.register(new CommandSpec(List.of("claims", "untrust"),
                "Removes a player's trust here: /rock claims untrust <player>", "rock.claims.trust", ctx -> {
            if (ctx.args().isEmpty()) {
                return CommandResult.USAGE_ERROR;
            }
            return claimAtSender(ctx).map(claim -> {
                if (!isManager(claim, ctx.sender().playerId())) {
                    ctx.sender().sendMessage("Only claim managers can untrust players.");
                    return CommandResult.NO_PERMISSION;
                }
                var target = services.find(PlayerService.class)
                        .flatMap(players -> players.findByUsername(ctx.args().get(0)).join());
                if (target.isEmpty()) {
                    return CommandResult.USAGE_ERROR;
                }
                claims.untrust(claim.id(), target.get().id()).join();
                ctx.sender().sendMessage(target.get().username() + " untrusted.");
                return CommandResult.SUCCESS;
            }).orElse(CommandResult.USAGE_ERROR);
        }));

        commands.register(new CommandSpec(List.of("claims", "abandon"),
                "Deletes the claim you stand in", "rock.claims.create", ctx ->
                claimAtSender(ctx).map(claim -> {
                    if (!isManager(claim, ctx.sender().playerId())) {
                        ctx.sender().sendMessage("Only claim managers can abandon a claim.");
                        return CommandResult.NO_PERMISSION;
                    }
                    claims.delete(claim.id()).join();
                    ctx.sender().sendMessage("Claim '" + claim.displayName() + "' abandoned.");
                    return CommandResult.SUCCESS;
                }).orElse(CommandResult.USAGE_ERROR)));
    }

    private boolean isManager(RockClaim claim, UUID playerId) {
        return claims.effectiveRole(claim, playerId)
                .map(role -> role.atLeast(ClaimRole.MANAGER))
                .orElse(false);
    }

    private static String rootMessage(Throwable t) {
        Throwable cause = t;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause.getMessage();
    }

    @Override
    public void onDisable() {
        for (String sub : List.of("claim", "info", "trust", "untrust", "abandon")) {
            commands.unregister(List.of("claims", sub));
        }
    }
}
