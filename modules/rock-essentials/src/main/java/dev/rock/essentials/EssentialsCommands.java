package dev.rock.essentials;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.command.CommandResult;
import dev.rock.api.command.CommandService;
import dev.rock.api.command.CommandSpec;
import dev.rock.api.domain.RockLocation;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.api.services.EssentialsService;
import dev.rock.api.services.PlayerService;
import dev.rock.api.world.PlayerTeleporter;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * /rock home|warp|tpa command surface. Persistence always works; physical
 * teleportation requires the loader-provided {@link PlayerTeleporter} and
 * degrades to an informative message without it.
 */
@RockInternal
@Singleton
public final class EssentialsCommands implements LifecycleAware {

    private final CommandService commands;
    private final EssentialsService essentials;
    private final PlayerService players;
    private final ServiceRegistry services;

    @Inject
    public EssentialsCommands(CommandService commands, EssentialsService essentials,
            PlayerService players, ServiceRegistry services) {
        this.commands = commands;
        this.essentials = essentials;
        this.players = players;
        this.services = services;
    }

    private Optional<PlayerTeleporter> teleporter() {
        return services.find(PlayerTeleporter.class);
    }

    private void teleportOrExplain(UUID playerId, RockLocation location,
            dev.rock.api.command.CommandSender sender, String destination) {
        Optional<PlayerTeleporter> teleporter = teleporter();
        if (teleporter.isPresent()) {
            teleporter.get().teleport(playerId, location)
                    .thenRun(() -> sender.sendMessage("Teleported to " + destination + "."));
        } else {
            sender.sendMessage("Saved position for " + destination
                    + " (teleportation requires loader support).");
        }
    }

    @Override
    public void onEnable() {
        commands.register(new CommandSpec(List.of("sethome"), "Saves a home at your position",
                "rock.essentials.home", ctx -> {
            if (ctx.sender().playerId() == null || ctx.sender().location() == null) {
                ctx.sender().sendMessage("Only in-game players can set homes.");
                return CommandResult.FAILURE;
            }
            String name = ctx.arg(0, "home");
            try {
                essentials.setHome(ctx.sender().playerId(), name, ctx.sender().location()).join();
                ctx.sender().sendMessage("Home '" + name + "' set.");
                return CommandResult.SUCCESS;
            } catch (Exception e) {
                ctx.sender().sendMessage(rootMessage(e));
                return CommandResult.FAILURE;
            }
        }));

        commands.register(new CommandSpec(List.of("home"), "Teleports you to a home",
                "rock.essentials.home", ctx -> {
            UUID playerId = ctx.sender().playerId();
            if (playerId == null) {
                return CommandResult.USAGE_ERROR;
            }
            String name = ctx.arg(0, "home");
            Optional<RockLocation> home = essentials.home(playerId, name).join();
            if (home.isEmpty()) {
                ctx.sender().sendMessage("No home named '" + name + "'. Homes: "
                        + essentials.homes(playerId).join());
                return CommandResult.USAGE_ERROR;
            }
            teleportOrExplain(playerId, home.get(), ctx.sender(), "home '" + name + "'");
            return CommandResult.SUCCESS;
        }));

        commands.register(new CommandSpec(List.of("homes"), "Lists your homes",
                "rock.essentials.home", ctx -> {
            ctx.sender().sendMessage("Homes: " + essentials.homes(ctx.sender().playerId()).join());
            return CommandResult.SUCCESS;
        }));

        commands.register(new CommandSpec(List.of("setwarp"), "Creates a server warp",
                "rock.essentials.admin.setwarp", ctx -> {
            if (ctx.sender().location() == null || ctx.args().isEmpty()) {
                return CommandResult.USAGE_ERROR;
            }
            essentials.setWarp(ctx.args().getFirst(), ctx.sender().location(), ctx.sender().playerId()).join();
            ctx.sender().sendMessage("Warp '" + ctx.args().getFirst() + "' set.");
            return CommandResult.SUCCESS;
        }));

        commands.register(new CommandSpec(List.of("warp"), "Teleports you to a warp",
                "rock.essentials.warp", ctx -> {
            if (ctx.args().isEmpty()) {
                ctx.sender().sendMessage("Warps: " + essentials.warps().join());
                return CommandResult.USAGE_ERROR;
            }
            Optional<RockLocation> warp = essentials.warp(ctx.args().getFirst()).join();
            if (warp.isEmpty()) {
                ctx.sender().sendMessage("No warp named '" + ctx.args().getFirst() + "'.");
                return CommandResult.USAGE_ERROR;
            }
            teleportOrExplain(ctx.sender().playerId(), warp.get(), ctx.sender(),
                    "warp '" + ctx.args().getFirst() + "'");
            return CommandResult.SUCCESS;
        }));

        commands.register(new CommandSpec(List.of("tpa"), "Requests teleport to a player",
                "rock.essentials.tpa", ctx -> {
            if (ctx.args().isEmpty() || ctx.sender().playerId() == null) {
                return CommandResult.USAGE_ERROR;
            }
            var target = players.findByUsername(ctx.args().getFirst()).join();
            if (target.isEmpty()) {
                ctx.sender().sendMessage("Unknown player: " + ctx.args().getFirst());
                return CommandResult.USAGE_ERROR;
            }
            essentials.tpa(ctx.sender().playerId(), target.get().id());
            ctx.sender().sendMessage("Teleport request sent to " + target.get().username() + ".");
            return CommandResult.SUCCESS;
        }));

        commands.register(new CommandSpec(List.of("tpaccept"), "Accepts a pending teleport request",
                "rock.essentials.tpa", ctx -> {
            UUID target = ctx.sender().playerId();
            Optional<UUID> requester = essentials.tpaccept(target);
            if (requester.isEmpty()) {
                ctx.sender().sendMessage("No pending teleport request.");
                return CommandResult.USAGE_ERROR;
            }
            Optional<PlayerTeleporter> teleporter = teleporter();
            if (teleporter.isPresent()) {
                teleporter.get().locate(target)
                        .thenCompose(location -> teleporter.get().teleport(requester.get(), location))
                        .thenRun(() -> ctx.sender().sendMessage("Teleport request accepted."));
            } else {
                ctx.sender().sendMessage("Request accepted (teleportation requires loader support).");
            }
            return CommandResult.SUCCESS;
        }));

        commands.register(new CommandSpec(List.of("tpdeny"), "Denies a pending teleport request",
                "rock.essentials.tpa", ctx -> {
            boolean denied = essentials.tpdeny(ctx.sender().playerId()).isPresent();
            ctx.sender().sendMessage(denied ? "Teleport request denied." : "No pending teleport request.");
            return denied ? CommandResult.SUCCESS : CommandResult.USAGE_ERROR;
        }));
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
        for (String name : List.of("sethome", "home", "homes", "setwarp", "warp", "tpa", "tpaccept", "tpdeny")) {
            commands.unregister(List.of(name));
        }
    }
}
