package dev.rock.metrics;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import dev.rock.api.module.ModuleManifest;
import dev.rock.api.module.RockModule;
import java.util.List;

/** rock-metrics module entrypoint, discovered via ServiceLoader. */
public final class MetricsRockModule implements RockModule {

    @Override
    public ModuleManifest manifest() {
        return new ModuleManifest(
                "rock-metrics", "Rock Metrics", "1.6.0", "1.6",
                List.of("ROCK SUITE Founding Developer Team"),
                List.of("rock-core"));
    }

    @Override
    public Object guiceModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(MetricsModuleService.class).in(Scopes.SINGLETON);
            }
        };
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }
}
