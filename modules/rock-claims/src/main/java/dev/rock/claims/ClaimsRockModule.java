package dev.rock.claims;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import dev.rock.api.module.ModuleManifest;
import dev.rock.api.module.RockModule;
import dev.rock.api.services.ClaimService;
import java.util.List;

/** rock-claims module entrypoint, discovered via ServiceLoader. */
public final class ClaimsRockModule implements RockModule {

    @Override
    public ModuleManifest manifest() {
        return new ModuleManifest(
                "rock-claims", "Rock Claims", "2.0.0", "2.0",
                List.of("ROCK SUITE Founding Developer Team"),
                List.of("rock-core", "rock-data", "rock-permissions"));
    }

    @Override
    public Object guiceModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(ClaimRepository.class).to(DataServiceClaimRepository.class).in(Scopes.SINGLETON);
                bind(DataServiceClaimRepository.class).in(Scopes.SINGLETON);
                bind(ClaimService.class).to(DefaultClaimService.class).in(Scopes.SINGLETON);
                bind(DefaultClaimService.class).in(Scopes.SINGLETON);
                bind(ClaimProtectionListener.class).in(Scopes.SINGLETON);
                bind(ClaimsCommands.class).in(Scopes.SINGLETON);
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
