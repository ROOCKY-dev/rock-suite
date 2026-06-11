package dev.rock.loader.neoforge;

import dev.rock.api.config.RockConfig;
import dev.rock.api.events.world.BlockChangeType;
import dev.rock.core.bootstrap.PlatformEnvironment;
import dev.rock.core.config.TomlConfigEngine;
import dev.rock.core.loader.LoaderBootstrap;
import dev.rock.data.DatabaseSettings;
import dev.rock.data.RockDataModule;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * NeoForge loader adapter (REH §4): the ONLY NeoForge-aware ROCK component on
 * this loader. Maps NeoForge bus events onto the ROCK EventBus; modules never
 * see a NeoForge class (AVD Rule Zero).
 */
@Mod("rock_suite")
public final class RockNeoForgeMod {

    private static final Logger log = LoggerFactory.getLogger(RockNeoForgeMod.class);

    private volatile LoaderBootstrap.BootResult boot;

    public RockNeoForgeMod() {
        NeoForge.EVENT_BUS.addListener((Consumer<ServerStartedEvent>) event -> start(event));
        NeoForge.EVENT_BUS.addListener((Consumer<ServerStoppingEvent>) event -> stop());
        NeoForge.EVENT_BUS.addListener((Consumer<PlayerEvent.PlayerLoggedInEvent>) event -> {
            LoaderBootstrap.BootResult current = boot;
            if (current != null) {
                Player player = event.getEntity();
                current.sessions().playerJoined(player.getUUID(), player.getScoreboardName());
            }
        });
        NeoForge.EVENT_BUS.addListener((Consumer<PlayerEvent.PlayerLoggedOutEvent>) event -> {
            LoaderBootstrap.BootResult current = boot;
            if (current != null) {
                Player player = event.getEntity();
                current.sessions().playerLeft(player.getUUID(), player.getScoreboardName());
            }
        });

        // World-interaction layer (K1): break + place feed claims protection
        // and block logging. Fake-player classification lands with the
        // ModDevGradle packaging step.
        NeoForge.EVENT_BUS.addListener((Consumer<BlockEvent.BreakEvent>) event -> {
            LoaderBootstrap.BootResult current = boot;
            if (current == null) {
                return;
            }
            UUID worldId = current.worldEvents().worldId(event.getLevel().dimensionKey());
            boolean allowed = current.worldEvents().blockChange(
                    event.getPlayer().getUUID(), false, worldId,
                    event.getPos().getX(), event.getPos().getY(), event.getPos().getZ(),
                    BlockChangeType.BREAK, event.getState().registryId(), "minecraft:air");
            if (!allowed) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((Consumer<BlockEvent.EntityPlaceEvent>) event -> {
            LoaderBootstrap.BootResult current = boot;
            if (current == null) {
                return;
            }
            Player placer = event.getPlacer();
            UUID worldId = current.worldEvents().worldId(event.getLevel().dimensionKey());
            boolean allowed = current.worldEvents().blockChange(
                    placer == null ? null : placer.getUUID(), false, worldId,
                    event.getPos().getX(), event.getPos().getY(), event.getPos().getZ(),
                    BlockChangeType.PLACE, event.getState().registryId(),
                    event.getPlacedBlock().registryId());
            if (!allowed) {
                event.setCanceled(true);
            }
        });
    }

    private void start(ServerStartedEvent event) {
        PlatformEnvironment environment = new NeoForgePlatformEnvironment(event.getServer());
        RockConfig config = new TomlConfigEngine(environment.dataDirectory().resolve("config"), System::getenv)
                .loadModuleConfig("rock", RockFabricDefaults.DEFAULT_CONFIG);
        DatabaseSettings settings = DatabaseSettings.fromConfig(config, environment.dataDirectory());
        boot = LoaderBootstrap.boot(environment, List.of(new RockDataModule(settings)));
        log.info("ROCK SUITE started on NeoForge");
    }

    private void stop() {
        LoaderBootstrap.BootResult current = boot;
        if (current != null) {
            current.platform().close();
            boot = null;
        }
    }

    /** Default platform config shared with no other loader (kept local to loaders/). */
    static final class RockFabricDefaults {
        static final String DEFAULT_CONFIG = """
                # ROCK SUITE platform configuration
                [database]
                # url = "jdbc:postgresql://localhost:5432/rock"
                # username = "rock"
                # password = "${env.ROCK_DB_PASSWORD}"

                [database.pool]
                maximum-pool-size = 10
                minimum-idle = 2
                """;

        private RockFabricDefaults() {
        }
    }
}
