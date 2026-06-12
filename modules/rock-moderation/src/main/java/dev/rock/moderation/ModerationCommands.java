package dev.rock.moderation;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.command.CommandContext;
import dev.rock.api.command.CommandResult;
import dev.rock.api.command.CommandService;
import dev.rock.api.command.CommandSpec;
import dev.rock.api.domain.PunishmentType;
import dev.rock.api.domain.RockPlayer;
import dev.rock.api.domain.RockPunishment;
import dev.rock.api.domain.owner.OwnerReference;
import dev.rock.api.domain.owner.PlayerOwner;
import dev.rock.api.domain.owner.SystemOwner;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.services.PlayerService;
import dev.rock.api.services.PunishmentService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * /rock ban|unban|mute|unmute|warn|history — moderation command surface.
 * Durations: "30m", "2h", "7d", "perm". Issuer is the sender (SYSTEM for console).
 */
@RockInternal
@Singleton
public final class ModerationCommands implements LifecycleAware {

    private final CommandService commands;
    private final PunishmentService punishments;
    private final PlayerService players;

    @Inject
    public ModerationCommands(CommandService commands, PunishmentService punishments, PlayerService players) {
        this.commands = commands;
        this.punishments = punishments;
        this.players = players;
    }

    private Optional<RockPlayer> target(CommandContext ctx) {
        if (ctx.args().isEmpty()) {
            ctx.sender().sendMessage("Usage: specify a player name.");
            return Optional.empty();
        }
        Optional<RockPlayer> target = players.findByUsername(ctx.args().getFirst()).join();
        if (target.isEmpty()) {
            ctx.sender().sendMessage("Unknown player: " + ctx.args().getFirst());
        }
        return target;
    }

    private static OwnerReference issuer(CommandContext ctx) {
        return ctx.sender().playerId() == null
                ? SystemOwner.server() : new PlayerOwner(ctx.sender().playerId());
    }

    private CommandResult applyPunishment(CommandContext ctx, PunishmentType type) {
        Optional<RockPlayer> target = target(ctx);
        if (target.isEmpty()) {
            return CommandResult.USAGE_ERROR;
        }
        Duration duration = null;
        int reasonFrom = 1;
        if (ctx.args().size() > 1) {
            try {
                duration = DurationParser.parse(ctx.args().get(1)).orElse(null);
                reasonFrom = 2;
            } catch (IllegalArgumentException e) {
                duration = null; // second arg is part of the reason, not a duration
            }
        }
        String reason = ctx.args().size() > reasonFrom
                ? String.join(" ", ctx.args().subList(reasonFrom, ctx.args().size()))
                : "No reason given";
        try {
            RockPunishment punishment =
                    punishments.punish(type, target.get().id(), issuer(ctx), reason, duration).join();
            String until = punishment.expires() == null ? "permanently" : "until " + punishment.expires();
            ctx.sender().sendMessage(type + " applied to " + target.get().username() + " " + until + ".");
            return CommandResult.SUCCESS;
        } catch (Exception e) {
            ctx.sender().sendMessage("Could not apply: " + e.getMessage());
            return CommandResult.FAILURE;
        }
    }

    private CommandResult revokeType(CommandContext ctx, PunishmentType type) {
        Optional<RockPlayer> target = target(ctx);
        if (target.isEmpty()) {
            return CommandResult.USAGE_ERROR;
        }
        Optional<RockPunishment> active = punishments.activeCached(target.get().id(), type);
        if (active.isEmpty()) {
            ctx.sender().sendMessage(target.get().username() + " has no active " + type + ".");
            return CommandResult.USAGE_ERROR;
        }
        punishments.revoke(active.get().id(),
                ctx.sender().playerId() == null ? SystemOwner.SERVER_SYSTEM_ID : ctx.sender().playerId()).join();
        ctx.sender().sendMessage(type + " revoked for " + target.get().username() + ".");
        return CommandResult.SUCCESS;
    }

    @Override
    public void onEnable() {
        commands.register(new CommandSpec(List.of("ban"), "Bans a player: /rock ban <player> [duration] [reason]",
                "rock.moderation.ban", ctx -> applyPunishment(ctx, PunishmentType.BAN)));
        commands.register(new CommandSpec(List.of("unban"), "Lifts a ban",
                "rock.moderation.ban", ctx -> revokeType(ctx, PunishmentType.BAN)));
        commands.register(new CommandSpec(List.of("mute"), "Mutes a player: /rock mute <player> [duration] [reason]",
                "rock.moderation.mute", ctx -> applyPunishment(ctx, PunishmentType.MUTE)));
        commands.register(new CommandSpec(List.of("unmute"), "Lifts a mute",
                "rock.moderation.mute", ctx -> revokeType(ctx, PunishmentType.MUTE)));
        commands.register(new CommandSpec(List.of("warn"), "Warns a player: /rock warn <player> <reason>",
                "rock.moderation.warn", ctx -> applyPunishment(ctx, PunishmentType.WARN)));
        commands.register(new CommandSpec(List.of("history"), "Shows a player's punishment history",
                "rock.moderation.history", ctx -> {
            Optional<RockPlayer> target = target(ctx);
            if (target.isEmpty()) {
                return CommandResult.USAGE_ERROR;
            }
            List<RockPunishment> history = punishments.history(target.get().id()).join();
            ctx.sender().sendMessage("History of " + target.get().username() + " (" + history.size() + "):");
            for (RockPunishment p : history) {
                String state = p.revokedAt() != null ? "revoked"
                        : p.activeAt(java.time.Instant.now()) ? "ACTIVE" : "expired";
                ctx.sender().sendMessage("  " + p.type() + " [" + state + "] — " + p.reason());
            }
            return CommandResult.SUCCESS;
        }));
    }

    @Override
    public void onDisable() {
        for (String name : List.of("ban", "unban", "mute", "unmute", "warn", "history")) {
            commands.unregister(List.of(name));
        }
    }
}
