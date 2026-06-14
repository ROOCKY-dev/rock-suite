package dev.rock.web;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import dev.rock.api.module.ModuleManifest;
import dev.rock.api.module.RockModule;
import dev.rock.web.auth.WebAccountRepository;
import java.util.List;

/** rock-web module entrypoint, discovered via ServiceLoader. */
public final class WebRockModule implements RockModule {

    @Override
    public ModuleManifest manifest() {
        return new ModuleManifest(
                "rock-web", "Rock Web", "2.0.0", "2.0",
                List.of("ROCK SUITE Founding Developer Team"),
                List.of("rock-core", "rock-data"));
    }

    @Override
    public Object guiceModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(WebAccountRepository.class).in(Scopes.SINGLETON);
                bind(WebModuleService.class).in(Scopes.SINGLETON);
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
