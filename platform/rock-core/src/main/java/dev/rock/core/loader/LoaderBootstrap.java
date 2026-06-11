package dev.rock.core.loader;

import com.google.inject.Module;
import dev.rock.api.event.EventBus;
import dev.rock.api.module.RockModule;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.core.bootstrap.PlatformEnvironment;
import dev.rock.core.bootstrap.RockPlatform;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Shared composition-root helper for loader adapters. Feature modules are
 * discovered via {@link ServiceLoader} ({@code META-INF/services/dev.rock.api.module.RockModule}),
 * so loaders have no compile dependency on any feature module (REH §8).
 */
public final class LoaderBootstrap {

    private static final Logger log = LoggerFactory.getLogger(LoaderBootstrap.class);

    private LoaderBootstrap() {
    }

    public static List<RockModule> discoverModules(ClassLoader classLoader) {
        List<RockModule> modules = new ArrayList<>();
        for (RockModule module : ServiceLoader.load(RockModule.class, classLoader)) {
            modules.add(module);
        }
        log.info("Discovered {} ROCK module(s) via ServiceLoader", modules.size());
        return modules;
    }

    /** Boots the platform with discovered modules and returns it with a session bridge attached. */
    public static BootResult boot(PlatformEnvironment environment, List<Module> contributedModules) {
        List<RockModule> modules = discoverModules(LoaderBootstrap.class.getClassLoader());
        RockPlatform platform = RockPlatform.boot(environment, contributedModules, modules);
        ServiceRegistry services = platform.services();
        EventBus eventBus = services.require(EventBus.class);
        PlayerSessionBridge sessions = new PlayerSessionBridge(services, eventBus);
        WorldEventBridge worldEvents = new WorldEventBridge(eventBus);
        return new BootResult(platform, sessions, worldEvents);
    }

    public record BootResult(RockPlatform platform, PlayerSessionBridge sessions, WorldEventBridge worldEvents) {
    }
}
