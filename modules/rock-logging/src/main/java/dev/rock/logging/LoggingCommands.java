package dev.rock.logging;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.command.CommandContext;
import dev.rock.api.command.CommandResult;
import dev.rock.api.command.CommandService;
import dev.rock.api.command.CommandSpec;
import dev.rock.api.domain.RockLocation;
import dev.rock.api.domain.RockWorldLogEntry;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.services.LogQuery;
import dev.rock.api.services.RollbackPreview;
import dev.rock.api.services.WorldLogService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

/**
 * /rock log near|preview|rollback|restore — the admin grief-repair surface
 * (CoreProtect command-grammar equivalent, scoped around the sender's position).
 */
@RockInternal
@Singleton
public final class LoggingCommands implements LifecycleAware {

    private static final String ADMIN_NODE = "rock.logging.admin";

    private final CommandService commands;
    private final WorldLogService worldLog;

    @Inject
    public LoggingCommands(CommandService commands, WorldLogService worldLog) {
        this.commands = commands;
        this.worldLog = worldLog;
    }

    private LogQuery aroundSender(CommandContext ctx, int radius) {
        RockLocation location = ctx.sender().location();
        if (location == null) {
            throw new IllegalStateException("This command needs an in-game position.");
        }
        return LogQuery.builder()
                .world(location.worldId())
                .around((int) location.x(), (int) location.y(), (int) location.z(), radius)
                .limit(5000)
                .build();
    }

    private int radiusArg(CommandContext ctx, int index) {
        return Integer.parseInt(ctx.arg(index, "10"));
    }

    @Override
    public void onEnable() {
        commands.register(new CommandSpec(List.of("log", "near"),
                "Recent changes around you: /rock log near [radius]", ADMIN_NODE, ctx -> {
            List<RockWorldLogEntry> entries = worldLog.query(aroundSender(ctx, radiusArg(ctx, 0))).join();
            ctx.sender().sendMessage("Changes nearby: " + entries.size());
            entries.stream().limit(10).forEach(entry -> ctx.sender().sendMessage(
                    "  " + entry.action() + " " + entry.blockBefore() + "→" + entry.blockAfter()
                            + " at " + entry.x() + "," + entry.y() + "," + entry.z()
                            + (entry.actor() == null ? " (environment)" : " by " + entry.actor())));
            return CommandResult.SUCCESS;
        }));

        commands.register(new CommandSpec(List.of("log", "preview"),
                "Dry-run a rollback: /rock log preview [radius]", ADMIN_NODE, ctx -> {
            RollbackPreview preview = worldLog.previewRollback(aroundSender(ctx, radiusArg(ctx, 0))).join();
            if (preview.empty()) {
                ctx.sender().sendMessage("Nothing to roll back here.");
                return CommandResult.SUCCESS;
            }
            ctx.sender().sendMessage("Rollback would revert " + preview.entries() + " change(s):");
            preview.byAction().forEach((action, count) ->
                    ctx.sender().sendMessage("  " + action + ": " + count));
            ctx.sender().sendMessage("Run /rock log rollback to apply.");
            return CommandResult.SUCCESS;
        }));

        commands.register(new CommandSpec(List.of("log", "rollback"),
                "Reverts changes around you: /rock log rollback [radius]", ADMIN_NODE, ctx -> {
            int reverted = worldLog.rollback(aroundSender(ctx, radiusArg(ctx, 0))).join();
            ctx.sender().sendMessage("Rolled back " + reverted + " change(s).");
            return CommandResult.SUCCESS;
        }));

        commands.register(new CommandSpec(List.of("log", "restore"),
                "Re-applies rolled-back changes: /rock log restore [radius]", ADMIN_NODE, ctx -> {
            int restored = worldLog.restore(aroundSender(ctx, radiusArg(ctx, 0))).join();
            ctx.sender().sendMessage("Restored " + restored + " change(s).");
            return CommandResult.SUCCESS;
        }));
    }

    @Override
    public void onDisable() {
        for (String sub : List.of("near", "preview", "rollback", "restore")) {
            commands.unregister(List.of("log", sub));
        }
    }
}
