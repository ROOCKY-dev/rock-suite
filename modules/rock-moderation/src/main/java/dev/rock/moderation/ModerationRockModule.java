package dev.rock.moderation;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import dev.rock.api.module.ModuleManifest;
import dev.rock.api.module.RockModule;
import dev.rock.api.services.PunishmentService;
import java.util.List;

/** rock-moderation module entrypoint, discovered via ServiceLoader. */
public final class ModerationRockModule implements RockModule {

    @Override
    public ModuleManifest manifest() {
        return new ModuleManifest(
                "rock-moderation", "Rock Moderation", "1.6.0", "1.6",
                List.of("ROCK SUITE Founding Developer Team"),
                List.of("rock-core", "rock-data", "rock-permissions"));
    }

    @Override
    public Object guiceModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(PunishmentService.class).to(DefaultPunishmentService.class).in(Scopes.SINGLETON);
                bind(DefaultPunishmentService.class).in(Scopes.SINGLETON);
                bind(MuteEnforcementListener.class).in(Scopes.SINGLETON);
                bind(ModerationCommands.class).in(Scopes.SINGLETON);
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
