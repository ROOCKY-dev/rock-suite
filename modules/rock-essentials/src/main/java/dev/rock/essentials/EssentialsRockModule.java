package dev.rock.essentials;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import dev.rock.api.module.ModuleManifest;
import dev.rock.api.module.RockModule;
import dev.rock.api.services.EssentialsService;
import java.util.List;

/** rock-essentials module entrypoint, discovered via ServiceLoader. */
public final class EssentialsRockModule implements RockModule {

    @Override
    public ModuleManifest manifest() {
        return new ModuleManifest(
                "rock-essentials", "Rock Essentials", "1.6.0", "1.6",
                List.of("ROCK SUITE Founding Developer Team"),
                List.of("rock-core", "rock-data", "rock-permissions"));
    }

    @Override
    public Object guiceModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(EssentialsService.class).to(DefaultEssentialsService.class).in(Scopes.SINGLETON);
                bind(DefaultEssentialsService.class).in(Scopes.SINGLETON);
                bind(EssentialsCommands.class).in(Scopes.SINGLETON);
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
