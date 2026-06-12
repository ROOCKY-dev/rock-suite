package dev.rock.teams;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import dev.rock.api.module.ModuleManifest;
import dev.rock.api.module.RockModule;
import dev.rock.api.services.TeamService;
import java.util.List;

/** rock-teams module entrypoint, discovered via ServiceLoader. */
public final class TeamsRockModule implements RockModule {

    @Override
    public ModuleManifest manifest() {
        return new ModuleManifest(
                "rock-teams", "Rock Teams", "1.6.0", "1.6",
                List.of("ROCK SUITE Founding Developer Team"),
                List.of("rock-core", "rock-data"));
    }

    @Override
    public Object guiceModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(TeamService.class).to(DefaultTeamService.class).in(Scopes.SINGLETON);
                bind(DefaultTeamService.class).in(Scopes.SINGLETON);
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
