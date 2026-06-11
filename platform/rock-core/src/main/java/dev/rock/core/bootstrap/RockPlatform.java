package dev.rock.core.bootstrap;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Stage;
import dev.rock.api.command.CommandResult;
import dev.rock.api.command.CommandService;
import dev.rock.api.command.CommandSpec;
import dev.rock.api.event.EventBus;
import dev.rock.api.module.RockModule;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.core.guice.RockCoreModule;
import dev.rock.core.module.ModuleLoader;
import dev.rock.core.scheduler.RockScheduler;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platform composition root (RPS §22). Loader adapters call
 * {@link #boot(PlatformEnvironment, List, List)} with any contributed platform
 * modules (e.g. rock-data's RockDataModule — Architectural Review D-1) and the
 * discovered feature modules.
 *
 * <p>Stage.PRODUCTION: eager singletons, deterministic boot failures (TRS §17).
 */
public final class RockPlatform implements AutoCloseable {

    public static final String VERSION = "1.1.0";

    private static final Logger log = LoggerFactory.getLogger(RockPlatform.class);

    private final Injector platformInjector;
    private final ModuleLoader moduleLoader;
    private final RockCoreModule coreModule;

    private RockPlatform(Injector platformInjector, ModuleLoader moduleLoader, RockCoreModule coreModule) {
        this.platformInjector = platformInjector;
        this.moduleLoader = moduleLoader;
        this.coreModule = coreModule;
    }

    public static RockPlatform boot(
            PlatformEnvironment environment, List<Module> contributedModules, List<RockModule> rockModules) {
        log.info("ROCK platform {} booting on {} ({})", VERSION,
                environment.serverInfo().type(), environment.serverInfo().version());

        RockCoreModule core = new RockCoreModule(environment);
        List<Module> all = new ArrayList<>();
        all.add(core);
        all.addAll(contributedModules);

        Injector injector = Guice.createInjector(Stage.PRODUCTION, all);

        // Enable platform-level LifecycleAware services before any module starts.
        core.lifecycleManager().enablePending();

        ModuleLoader loader = new ModuleLoader(
                injector, injector.getInstance(EventBus.class), core.lifecycleManager());
        RockPlatform platform = new RockPlatform(injector, loader, core);

        platform.registerBuiltinCommands(injector.getInstance(CommandService.class));

        loader.loadAll(rockModules);
        loader.enableAll();

        log.info("ROCK platform ready: {} module(s) {}", rockModules.size(), loader.states());
        return platform;
    }

    private void registerBuiltinCommands(CommandService commands) {
        commands.register(new CommandSpec(
                List.of("version"),
                "Shows the ROCK platform version",
                "",
                context -> {
                    context.sender().sendMessage("ROCK SUITE v" + VERSION);
                    return CommandResult.SUCCESS;
                }));
        commands.register(new CommandSpec(
                List.of("modules"),
                "Lists modules and their states",
                "rock.admin.modules",
                context -> {
                    moduleLoader.states().forEach((id, state) ->
                            context.sender().sendMessage(id + ": " + state));
                    return CommandResult.SUCCESS;
                }));
    }

    public Injector injector() {
        return platformInjector;
    }

    public ModuleLoader moduleLoader() {
        return moduleLoader;
    }

    public ServiceRegistry services() {
        return platformInjector.getInstance(ServiceRegistry.class);
    }

    /** Platform shutdown sequence (RPS §23). */
    @Override
    public void close() {
        log.info("ROCK platform shutting down");
        moduleLoader.disableAll();
        coreModule.lifecycleManager().disableAll();
        platformInjector.getInstance(RockScheduler.class).close();
    }
}
