package dev.rock.permissions;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.command.CommandContext;
import dev.rock.api.command.CommandResult;
import dev.rock.api.command.CommandService;
import dev.rock.api.command.CommandSpec;
import dev.rock.api.domain.RockGroup;
import dev.rock.api.domain.RockPlayer;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.api.services.PermissionService;
import dev.rock.api.services.PlayerService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Optional;

/**
 * /rock perms — the admin permission surface:
 * grant|deny|unset <player> <node>, check <player> <node>,
 * group create <name> <priority>, group grant <group> <node>,
 * group assign <player> <group>, setoption <player> <key> <value>.
 */
@RockInternal
@Singleton
public final class PermissionsCommands implements LifecycleAware {

    private static final String ADMIN_NODE = "rock.admin.permissions";

    private final CommandService commands;
    private final PermissionService permissions;
    private final ServiceRegistry services;

    @Inject
    public PermissionsCommands(CommandService commands, PermissionService permissions, ServiceRegistry services) {
        this.commands = commands;
        this.permissions = permissions;
        this.services = services;
    }

    private Optional<RockPlayer> player(CommandContext ctx, int argIndex) {
        // PlayerService lives in rock-data (platform level) — resolve via registry.
        Optional<RockPlayer> player = services.find(PlayerService.class)
                .flatMap(players -> players.findByUsername(ctx.args().get(argIndex)).join());
        if (player.isEmpty()) {
            ctx.sender().sendMessage("Unknown player: " + ctx.args().get(argIndex)
                    + " (players are known after their first join)");
        }
        return player;
    }

    @Override
    public void onEnable() {
        commands.register(new CommandSpec(List.of("perms", "grant"),
                "Grants a node: /rock perms grant <player> <node>", ADMIN_NODE, ctx -> {
            if (ctx.args().size() < 2) {
                return CommandResult.USAGE_ERROR;
            }
            return player(ctx, 0).map(target -> {
                permissions.grant(target.id(), ctx.args().get(1)).join();
                ctx.sender().sendMessage("Granted " + ctx.args().get(1) + " to " + target.username() + ".");
                return CommandResult.SUCCESS;
            }).orElse(CommandResult.USAGE_ERROR);
        }));

        commands.register(new CommandSpec(List.of("perms", "deny"),
                "Denies a node: /rock perms deny <player> <node>", ADMIN_NODE, ctx -> {
            if (ctx.args().size() < 2) {
                return CommandResult.USAGE_ERROR;
            }
            return player(ctx, 0).map(target -> {
                permissions.deny(target.id(), ctx.args().get(1)).join();
                ctx.sender().sendMessage("Denied " + ctx.args().get(1) + " for " + target.username() + ".");
                return CommandResult.SUCCESS;
            }).orElse(CommandResult.USAGE_ERROR);
        }));

        commands.register(new CommandSpec(List.of("perms", "unset"),
                "Removes a node: /rock perms unset <player> <node>", ADMIN_NODE, ctx -> {
            if (ctx.args().size() < 2) {
                return CommandResult.USAGE_ERROR;
            }
            return player(ctx, 0).map(target -> {
                permissions.unset(target.id(), ctx.args().get(1)).join();
                ctx.sender().sendMessage("Unset " + ctx.args().get(1) + " for " + target.username() + ".");
                return CommandResult.SUCCESS;
            }).orElse(CommandResult.USAGE_ERROR);
        }));

        commands.register(new CommandSpec(List.of("perms", "check"),
                "Evaluates a node: /rock perms check <player> <node>", ADMIN_NODE, ctx -> {
            if (ctx.args().size() < 2) {
                return CommandResult.USAGE_ERROR;
            }
            return player(ctx, 0).map(target -> {
                ctx.sender().sendMessage(target.username() + " → " + ctx.args().get(1) + " = "
                        + permissions.check(target.id(), ctx.args().get(1)));
                return CommandResult.SUCCESS;
            }).orElse(CommandResult.USAGE_ERROR);
        }));

        commands.register(new CommandSpec(List.of("perms", "group", "create"),
                "Creates a group: /rock perms group create <name> <priority>", ADMIN_NODE, ctx -> {
            if (ctx.args().size() < 2) {
                return CommandResult.USAGE_ERROR;
            }
            RockGroup group = permissions.createGroup(
                    ctx.args().get(0), Integer.parseInt(ctx.args().get(1))).join();
            ctx.sender().sendMessage("Group " + group.name() + " created (priority " + group.priority() + ").");
            return CommandResult.SUCCESS;
        }));

        commands.register(new CommandSpec(List.of("perms", "group", "grant"),
                "Grants a node to a group: /rock perms group grant <group> <node>", ADMIN_NODE, ctx -> {
            if (ctx.args().size() < 2) {
                return CommandResult.USAGE_ERROR;
            }
            return groupByName(ctx, 0).map(group -> {
                permissions.grantGroup(group.id(), ctx.args().get(1)).join();
                ctx.sender().sendMessage("Granted " + ctx.args().get(1) + " to group " + group.name() + ".");
                return CommandResult.SUCCESS;
            }).orElse(CommandResult.USAGE_ERROR);
        }));

        commands.register(new CommandSpec(List.of("perms", "group", "assign"),
                "Adds a player to a group: /rock perms group assign <player> <group>", ADMIN_NODE, ctx -> {
            if (ctx.args().size() < 2) {
                return CommandResult.USAGE_ERROR;
            }
            return player(ctx, 0).flatMap(target -> groupByName(ctx, 1).map(group -> {
                permissions.assignGroup(target.id(), group.id()).join();
                ctx.sender().sendMessage(target.username() + " added to " + group.name() + ".");
                return CommandResult.SUCCESS;
            })).orElse(CommandResult.USAGE_ERROR);
        }));

        commands.register(new CommandSpec(List.of("perms", "setoption"),
                "Sets player meta: /rock perms setoption <player> <key> <value>", ADMIN_NODE, ctx -> {
            if (ctx.args().size() < 3) {
                return CommandResult.USAGE_ERROR;
            }
            return player(ctx, 0).map(target -> {
                permissions.setPlayerOption(target.id(), ctx.args().get(1), ctx.args().get(2)).join();
                ctx.sender().sendMessage("Option set.");
                return CommandResult.SUCCESS;
            }).orElse(CommandResult.USAGE_ERROR);
        }));
    }

    private Optional<RockGroup> groupByName(CommandContext ctx, int argIndex) {
        // Cache-backed lookup via groupsOf is player-scoped; use repository-free
        // approach: createGroup is idempotent? No — search through reload snapshot
        // is not exposed. Pragmatic: groups are few; resolve via known names map
        // kept by the service? Not exposed either. Fall back to scanning players'
        // groups is wrong — so resolve through PermissionService.groupsOf is
        // unusable here. We track by listing: not available → use name-equality
        // over createGroup's published events? Simplest correct path: the
        // repository, but that's module-internal — and we ARE the module.
        Optional<RockGroup> group = ((DefaultPermissionService) permissions).groupByName(ctx.args().get(argIndex));
        if (group.isEmpty()) {
            ctx.sender().sendMessage("Unknown group: " + ctx.args().get(argIndex));
        }
        return group;
    }

    @Override
    public void onDisable() {
        commands.unregister(List.of("perms", "grant"));
        commands.unregister(List.of("perms", "deny"));
        commands.unregister(List.of("perms", "unset"));
        commands.unregister(List.of("perms", "check"));
        commands.unregister(List.of("perms", "group", "create"));
        commands.unregister(List.of("perms", "group", "grant"));
        commands.unregister(List.of("perms", "group", "assign"));
        commands.unregister(List.of("perms", "setoption"));
    }
}
