package dev.rock.discord;

import com.google.inject.AbstractModule;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import dev.rock.api.config.ConfigEngine;
import dev.rock.api.config.RockConfig;
import dev.rock.api.module.ModuleManifest;
import dev.rock.api.module.RockModule;
import dev.rock.api.services.DiscordService;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.slf4j.LoggerFactory;

/** rock-discord module entrypoint, discovered via ServiceLoader. */
public final class DiscordRockModule implements RockModule {

    static final String DEFAULT_CONFIG = """
            # ROCK Discord integration
            [discord]
            # Inject the bot token via environment — never commit it (TRS §11).
            # token = "${env.ROCK_DISCORD_TOKEN}"
            send-interval-ms = 250
            # Channel id for the MC<->Discord chat bridge; empty = disabled.
            chat-bridge-channel = ""
            """;

    @Override
    public ModuleManifest manifest() {
        return new ModuleManifest(
                "rock-discord", "Rock Discord", "2.0.0", "2.0",
                List.of("ROCK SUITE Founding Developer Team"),
                List.of("rock-core", "rock-data", "rock-permissions"));
    }

    @Override
    public Object guiceModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                bind(DiscordService.class).to(DefaultDiscordService.class).in(Scopes.SINGLETON);
                bind(DefaultDiscordService.class).in(Scopes.SINGLETON);
                bind(ChatBridgeListener.class).in(Scopes.SINGLETON);
            }

            @Provides
            @Singleton
            DiscordSettings provideSettings(ConfigEngine configEngine) {
                RockConfig config = configEngine.loadModuleConfig("rock-discord", DEFAULT_CONFIG);
                return DiscordSettings.fromConfig(config);
            }

            @Provides
            @Singleton
            DiscordMessageQueue provideQueue(DiscordSettings settings) {
                DiscordGateway gateway;
                if (settings.token().isBlank()) {
                    LoggerFactory.getLogger(DiscordRockModule.class)
                            .warn("No discord.token configured; Discord delivery is disabled (no-op gateway)");
                    gateway = (channel, content) -> CompletableFuture.completedFuture(null);
                } else {
                    gateway = new HttpDiscordGateway(settings.token());
                }
                return new DiscordMessageQueue(gateway, Duration.ofMillis(settings.sendIntervalMs()));
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
