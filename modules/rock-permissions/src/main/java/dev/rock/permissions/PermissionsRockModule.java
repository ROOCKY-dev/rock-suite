package dev.rock.permissions;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import dev.rock.api.module.ModuleManifest;
import dev.rock.api.module.RockModule;
import dev.rock.api.services.PermissionService;
import java.util.List;

/** rock-permissions module entrypoint, discovered via ServiceLoader. */
public final class PermissionsRockModule implements RockModule {

    @Override
    public ModuleManifest manifest() {
        return new ModuleManifest(
                "rock-permissions", "Rock Permissions", "1.0.0", "1.0",
                List.of("ROCK SUITE Founding Developer Team"),
                List.of("rock-core", "rock-data"));
    }

    @Override
    public Object guiceModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(PermissionRepository.class).to(DataServicePermissionRepository.class).in(Scopes.SINGLETON);
                bind(DataServicePermissionRepository.class).in(Scopes.SINGLETON);
                bind(PermissionService.class).to(DefaultPermissionService.class).in(Scopes.SINGLETON);
                bind(DefaultPermissionService.class).in(Scopes.SINGLETON);
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
