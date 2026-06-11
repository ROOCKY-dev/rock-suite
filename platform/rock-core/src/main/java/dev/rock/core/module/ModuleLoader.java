package dev.rock.core.module;

import com.google.inject.Injector;
import com.google.inject.Module;
import dev.rock.api.annotations.RockInternal;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.platform.ModuleLoadedEvent;
import dev.rock.api.events.platform.ModuleStartedEvent;
import dev.rock.api.events.platform.ModuleStoppedEvent;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.module.ModuleManifest;
import dev.rock.api.module.ModuleState;
import dev.rock.api.module.RockModule;
import dev.rock.core.lifecycle.LifecycleManager;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Discovers, orders, and drives ROCK modules (RPS §3).
 *
 * <p>Each module gets its own child injector of the platform injector
 * (DIS §3); modules never share injectors (DIS anti-pattern 4). A module that
 * fails to enable is isolated: it is marked FAILED and disabled while the
 * platform and all other modules keep running (TRS §17).
 */
@RockInternal
public final class ModuleLoader {

    private static final Logger log = LoggerFactory.getLogger(ModuleLoader.class);

    /** Platform-provided capabilities modules may declare as dependencies. */
    private static final Set<String> PLATFORM_CAPABILITIES = Set.of("rock-core", "rock-data", "rock-api");

    private final Injector platformInjector;
    private final EventBus eventBus;
    private final LifecycleManager lifecycleManager;

    private final Map<String, LoadedModule> modules = new LinkedHashMap<>();

    public ModuleLoader(Injector platformInjector, EventBus eventBus, LifecycleManager lifecycleManager) {
        this.platformInjector = platformInjector;
        this.eventBus = eventBus;
        this.lifecycleManager = lifecycleManager;
    }

    public static final class LoadedModule {
        private final RockModule module;
        private ModuleState state = ModuleState.DISCOVERED;
        private Injector injector;
        private List<LifecycleAware> lifecycleInstances = List.of();

        LoadedModule(RockModule module) {
            this.module = module;
        }

        public ModuleState state() {
            return state;
        }

        public ModuleManifest manifest() {
            return module.manifest();
        }
    }

    /** Validates manifests and resolves startup order; does not enable anything yet. */
    public void loadAll(List<RockModule> discovered) {
        Map<String, RockModule> byId = new HashMap<>();
        for (RockModule module : discovered) {
            ModuleManifest manifest = module.manifest();
            if (byId.putIfAbsent(manifest.id(), module) != null) {
                throw new IllegalStateException("Duplicate module id: " + manifest.id());
            }
        }
        for (RockModule module : topologicalOrder(byId)) {
            LoadedModule loaded = new LoadedModule(module);
            loaded.state = ModuleState.VALIDATED;
            module.onLoad();
            loaded.state = ModuleState.LOADED;
            modules.put(module.manifest().id(), loaded);
            eventBus.publish(new ModuleLoadedEvent(module.manifest()));
        }
    }

    /** Enables all loaded modules in dependency order with crash isolation. */
    public void enableAll() {
        for (LoadedModule loaded : modules.values()) {
            enable(loaded);
        }
    }

    private void enable(LoadedModule loaded) {
        ModuleManifest manifest = loaded.module.manifest();
        // A module whose dependency failed must not start.
        for (String dep : manifest.dependencies()) {
            LoadedModule depModule = modules.get(dep);
            if (depModule != null && depModule.state != ModuleState.RUNNING) {
                log.error("Module {} not started: dependency {} is {}", manifest.id(), dep,
                        depModule.state);
                loaded.state = ModuleState.FAILED;
                eventBus.publish(new ModuleStoppedEvent(manifest, true));
                return;
            }
        }
        try {
            loaded.injector = platformInjector.createChildInjector((Module) loaded.module.guiceModule());
            loaded.state = ModuleState.INITIALIZED;
            loaded.lifecycleInstances = lifecycleManager.enablePending();
            loaded.module.onEnable();
            loaded.state = ModuleState.RUNNING;
            eventBus.publish(new ModuleStartedEvent(manifest));
            log.info("Module {} {} enabled", manifest.id(), manifest.version());
        } catch (Exception e) {
            // Crash isolation (TRS §17): module disabled, everything else continues.
            log.error("Module {} failed to enable; disabling it (crash isolation)", manifest.id(), e);
            lifecycleManager.disable(loaded.lifecycleInstances);
            safeDisable(loaded.module);
            loaded.state = ModuleState.FAILED;
            eventBus.publish(new ModuleStoppedEvent(manifest, true));
        }
    }

    /** Disables all RUNNING modules in reverse start order. */
    public void disableAll() {
        Deque<LoadedModule> reverse = new ArrayDeque<>();
        modules.values().forEach(reverse::push);
        while (!reverse.isEmpty()) {
            LoadedModule loaded = reverse.pop();
            if (loaded.state != ModuleState.RUNNING) {
                continue;
            }
            loaded.state = ModuleState.STOPPING;
            safeDisable(loaded.module);
            lifecycleManager.disable(loaded.lifecycleInstances);
            loaded.state = ModuleState.UNLOADED;
            eventBus.publish(new ModuleStoppedEvent(loaded.module.manifest(), false));
        }
    }

    public Map<String, ModuleState> states() {
        Map<String, ModuleState> result = new LinkedHashMap<>();
        modules.forEach((id, m) -> result.put(id, m.state));
        return result;
    }

    private void safeDisable(RockModule module) {
        try {
            module.onDisable();
        } catch (Exception e) {
            log.error("Module {} onDisable threw", module.manifest().id(), e);
        }
    }

    private List<RockModule> topologicalOrder(Map<String, RockModule> byId) {
        List<RockModule> order = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();
        for (String id : byId.keySet()) {
            visit(id, byId, visited, visiting, order);
        }
        return order;
    }

    private void visit(String id, Map<String, RockModule> byId, Set<String> visited, Set<String> visiting,
            List<RockModule> order) {
        if (visited.contains(id)) {
            return;
        }
        if (!visiting.add(id)) {
            throw new IllegalStateException("Circular module dependency involving: " + id);
        }
        RockModule module = byId.get(id);
        if (module == null) {
            if (!PLATFORM_CAPABILITIES.contains(id)) {
                throw new IllegalStateException("Unknown module dependency: " + id);
            }
            visiting.remove(id);
            visited.add(id);
            return;
        }
        for (String dep : module.manifest().dependencies()) {
            visit(dep, byId, visited, visiting, order);
        }
        visiting.remove(id);
        visited.add(id);
        order.add(module);
    }
}
