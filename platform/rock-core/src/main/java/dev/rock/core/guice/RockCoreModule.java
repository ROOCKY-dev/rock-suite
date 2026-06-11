package dev.rock.core.guice;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import com.google.inject.name.Names;
import dev.rock.api.annotations.RockInternal;
import dev.rock.api.command.CommandService;
import dev.rock.api.config.ConfigEngine;
import dev.rock.api.event.EventBus;
import dev.rock.api.scheduler.Scheduler;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.core.bootstrap.PlatformEnvironment;
import dev.rock.core.command.DefaultCommandService;
import dev.rock.core.config.TomlConfigEngine;
import dev.rock.core.event.DefaultEventBus;
import dev.rock.core.guice.PlatformListeners.LifecycleListener;
import dev.rock.core.guice.PlatformListeners.ServiceRegistrationListener;
import dev.rock.core.guice.PlatformListeners.SubtypeMatcher;
import dev.rock.core.lifecycle.LifecycleManager;
import dev.rock.core.scheduler.RockScheduler;
import dev.rock.core.service.DefaultServiceRegistry;
import java.nio.file.Path;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * The platform Guice module (DIS §4, corrected per Architectural Review D-1):
 * rock-core binds only core services. rock-data contributes its own module —
 * no rock-data class is referenced here, which breaks the documented cycle.
 *
 * <p>The registry and lifecycle manager are constructed eagerly (outside
 * Guice) so the type listeners can use them during injector creation.
 */
@RockInternal
public final class RockCoreModule extends AbstractModule {

    private final PlatformEnvironment environment;
    private final DefaultServiceRegistry registry = new DefaultServiceRegistry();
    private final LifecycleManager lifecycleManager = new LifecycleManager();

    public RockCoreModule(PlatformEnvironment environment) {
        this.environment = Objects.requireNonNull(environment, "environment");
    }

    public LifecycleManager lifecycleManager() {
        return lifecycleManager;
    }

    public DefaultServiceRegistry registry() {
        return registry;
    }

    @Override
    protected void configure() {
        // Architectural Review D-3: no JIT bindings may leak to the platform
        // level; module isolation is enforced by Guice itself.
        binder().requireExplicitBindings();
        // No AOP: Fabric/NeoForge classloaders break runtime bytecode generation.
        binder().disableCircularProxies();

        bind(PlatformEnvironment.class).toInstance(environment);
        bind(Path.class).annotatedWith(Names.named("rock.config.dir"))
                .toInstance(environment.dataDirectory().resolve("config"));

        bind(ServiceRegistry.class).toInstance(registry);
        bind(LifecycleManager.class).toInstance(lifecycleManager);

        ExecutorService asyncExecutor = Executors.newVirtualThreadPerTaskExecutor();
        ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "rock-scheduler-timer");
            t.setDaemon(true);
            return t;
        });
        RockScheduler scheduler = new RockScheduler(environment.mainThreadExecutor(), asyncExecutor, timer);
        bind(Scheduler.class).toInstance(scheduler);
        bind(RockScheduler.class).toInstance(scheduler);

        DefaultEventBus eventBus = new DefaultEventBus(asyncExecutor);
        bind(EventBus.class).toInstance(eventBus);

        bind(ConfigEngine.class).to(TomlConfigEngine.class).in(Scopes.SINGLETON);
        bind(TomlConfigEngine.class).in(Scopes.SINGLETON);
        bind(CommandService.class).to(DefaultCommandService.class).in(Scopes.SINGLETON);
        bind(DefaultCommandService.class).in(Scopes.SINGLETON);

        // DIS §8 done correctly (Architectural Review D-2): single installation,
        // inherited by every module child injector.
        bindListener(new SubtypeMatcher(dev.rock.api.service.RockService.class),
                new ServiceRegistrationListener(registry));
        bindListener(new SubtypeMatcher(dev.rock.api.lifecycle.LifecycleAware.class),
                new LifecycleListener(lifecycleManager));

        // Manually register the instance-bound services (the type listeners only
        // see Guice-constructed instances).
        registry.replace(ServiceRegistry.class, registry);
        registry.replace(EventBus.class, eventBus);
        registry.replace(Scheduler.class, scheduler);
    }
}
