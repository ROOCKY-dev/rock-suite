package dev.rock.backup;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import dev.rock.api.config.ConfigEngine;
import dev.rock.api.module.ModuleManifest;
import dev.rock.api.module.RockModule;
import dev.rock.api.services.BackupService;
import jakarta.inject.Singleton;
import java.util.List;

/** rock-backup module entrypoint, discovered via ServiceLoader. */
public final class BackupRockModule implements RockModule {

    static final String DEFAULT_CONFIG = """
            # ROCK backup settings (TRS §14)
            [backup]
            # Minutes between scheduled backups; 0 disables the schedule.
            interval-minutes = 360
            # How many archives to keep when pruning.
            retention = 12
            """;

    @Override
    public ModuleManifest manifest() {
        return new ModuleManifest(
                "rock-backup", "Rock Backup", "1.6.0", "1.6",
                List.of("ROCK SUITE Founding Developer Team"),
                List.of("rock-core"));
    }

    @Override
    public Object guiceModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(BackupService.class).to(DefaultBackupService.class).in(Scopes.SINGLETON);
                bind(DefaultBackupService.class).in(Scopes.SINGLETON);
            }

            @Provides
            @Singleton
            BackupSettings provideSettings(ConfigEngine configEngine) {
                return BackupSettings.fromConfig(
                        configEngine.loadModuleConfig("rock-backup", DEFAULT_CONFIG));
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
