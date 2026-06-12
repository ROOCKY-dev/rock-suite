package dev.rock.migrate;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import dev.rock.api.module.ModuleManifest;
import dev.rock.api.module.RockModule;
import java.util.List;

/** rock-migrate (RMG) module entrypoint, discovered via ServiceLoader. */
public final class MigrateRockModule implements RockModule {

    @Override
    public ModuleManifest manifest() {
        return new ModuleManifest(
                "rock-migrate", "Rock Migrate (RMG)", "1.5.0", "1.5",
                List.of("ROCK SUITE Founding Developer Team"),
                List.of("rock-core", "rock-data", "rock-permissions"));
    }

    @Override
    public Object guiceModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(LuckPermsImporter.class).in(Scopes.SINGLETON);
                bind(EssentialsBalanceImporter.class).in(Scopes.SINGLETON);
                bind(MigrateCommands.class).in(Scopes.SINGLETON);
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
