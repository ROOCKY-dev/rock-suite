package dev.rock.migrate;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.command.CommandResult;
import dev.rock.api.command.CommandService;
import dev.rock.api.command.CommandSpec;
import dev.rock.api.lifecycle.LifecycleAware;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** /rock migrate <importer> <path> — runs an RMG importer against a source. */
@RockInternal
@Singleton
public final class MigrateCommands implements LifecycleAware {

    private final CommandService commands;
    private final Map<String, RmgImporter> importers = new LinkedHashMap<>();

    @Inject
    public MigrateCommands(CommandService commands, LuckPermsImporter luckPerms,
            EssentialsBalanceImporter essentials) {
        this.commands = commands;
        register(luckPerms);
        register(essentials);
    }

    private void register(RmgImporter importer) {
        importers.put(importer.id(), importer);
    }

    @Override
    public void onEnable() {
        commands.register(new CommandSpec(List.of("migrate"),
                "Imports incumbent data: /rock migrate <importer> <path>", "rock.admin.migrate", ctx -> {
            if (ctx.args().size() < 2) {
                ctx.sender().sendMessage("Importers:");
                importers.values().forEach(importer -> ctx.sender().sendMessage(
                        "  " + importer.id() + " — " + importer.description()));
                return CommandResult.USAGE_ERROR;
            }
            RmgImporter importer = importers.get(ctx.args().get(0).toLowerCase());
            if (importer == null) {
                ctx.sender().sendMessage("Unknown importer: " + ctx.args().get(0));
                return CommandResult.USAGE_ERROR;
            }
            try {
                RmgImporter.ImportReport report = importer.run(Path.of(ctx.args().get(1))).join();
                ctx.sender().sendMessage("Import complete: " + report.imported());
                report.warnings().stream().limit(10).forEach(warning ->
                        ctx.sender().sendMessage("  ⚠ " + warning));
                if (report.warnings().size() > 10) {
                    ctx.sender().sendMessage("  … " + (report.warnings().size() - 10) + " more warnings (see log)");
                }
                return CommandResult.SUCCESS;
            } catch (Exception e) {
                ctx.sender().sendMessage("Import failed: " + rootMessage(e));
                return CommandResult.FAILURE;
            }
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
        commands.unregister(List.of("migrate"));
    }
}
