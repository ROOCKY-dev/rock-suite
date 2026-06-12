package dev.rock.logging;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import dev.rock.api.module.ModuleManifest;
import dev.rock.api.module.RockModule;
import dev.rock.api.services.WorldLogService;
import jakarta.inject.Singleton;
import java.util.List;

/** rock-logging module entrypoint, discovered via ServiceLoader. */
public final class LoggingRockModule implements RockModule {

    @Override
    public ModuleManifest manifest() {
        return new ModuleManifest(
                "rock-logging", "Rock Logging", "1.2.0", "1.2",
                List.of("ROCK SUITE Founding Developer Team"),
                List.of("rock-core", "rock-data"));
    }

    @Override
    public Object guiceModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(WorldLogRepository.class).to(DataServiceWorldLogRepository.class).in(Scopes.SINGLETON);
                bind(DataServiceWorldLogRepository.class).in(Scopes.SINGLETON);
                bind(WorldLogService.class).to(DefaultWorldLogService.class).in(Scopes.SINGLETON);
                bind(DefaultWorldLogService.class).in(Scopes.SINGLETON);
            }

            @Provides
            @Singleton
            LogConsumer provideConsumer(WorldLogRepository repository) {
                return new LogConsumer(repository);
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
