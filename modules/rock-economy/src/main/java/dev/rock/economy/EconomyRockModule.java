package dev.rock.economy;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import dev.rock.api.module.ModuleManifest;
import dev.rock.api.module.RockModule;
import dev.rock.api.services.EconomyService;
import java.util.List;

/** rock-economy module entrypoint, discovered via ServiceLoader. */
public final class EconomyRockModule implements RockModule {

    @Override
    public ModuleManifest manifest() {
        return new ModuleManifest(
                "rock-economy", "Rock Economy", "2.0.0", "2.0",
                List.of("ROCK SUITE Founding Developer Team"),
                List.of("rock-core", "rock-data", "rock-permissions"));
    }

    @Override
    public Object guiceModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(EconomyService.class).to(DefaultEconomyService.class).in(Scopes.SINGLETON);
                bind(DefaultEconomyService.class).in(Scopes.SINGLETON);
                bind(EconomyCommands.class).in(Scopes.SINGLETON);
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
