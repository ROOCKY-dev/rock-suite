package dev.rock.loader.fabric;

import dev.rock.api.config.RockConfig;
import dev.rock.api.events.world.BlockChangeType;
import dev.rock.core.bootstrap.PlatformEnvironment;
import dev.rock.core.config.TomlConfigEngine;
import dev.rock.core.loader.LoaderBootstrap;
import dev.rock.data.DatabaseSettings;
import dev.rock.data.RockDataModule;
import java.util.List;
import java.util.UUID;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fabric loader adapter (REH §4): the ONLY Fabric-aware ROCK component on this
 * loader. Translates Fabric callbacks into ROCK platform events; modules never
 * see a Fabric class (AVD Rule Zero).
 */
public final class RockFabricMod implements DedicatedServerModInitializer {

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

    private static final Logger log = LoggerFactory.getLogger(RockFabricMod.class);

    private volatile LoaderBootstrap.BootResult boot;

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());

        // K2: route fabric-permissions-api checks into rock-permissions.
        // Optional dependency — skip cleanly when the API mod is absent.
        try {
            FabricPermissionsBridge.register(() -> boot);
        } catch (NoClassDefFoundError e) {
            log.info("fabric-permissions-api not installed; permission bridging disabled");
        }

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            LoaderBootstrap.BootResult current = boot;
            if (current != null) {
                ServerPlayer player = handler.getPlayer();
                current.sessions().playerJoined(player.getUUID(), player.getScoreboardName());
            }
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LoaderBootstrap.BootResult current = boot;
            if (current != null) {
                ServerPlayer player = handler.getPlayer();
                current.sessions().playerLeft(player.getUUID(), player.getScoreboardName());
            }
        });

        // World-interaction layer (K1): pre-action break check feeds claims
        // protection and block logging through one platform event.
        // Fake-player classification lands with the Loom packaging step (the
        // stub surface has no FakePlayer type).
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            LoaderBootstrap.BootResult current = boot;
            if (current == null) {
                return true;
            }
            UUID worldId = current.worldEvents().worldId(world.dimensionKey());
            return current.worldEvents().blockChange(
                    player.getUUID(), false, worldId, pos.getX(), pos.getY(), pos.getZ(),
                    BlockChangeType.BREAK, state.registryId(), "minecraft:air");
        });

        // Chat layer: pre-broadcast check feeds mutes/filters and the Discord bridge.
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            LoaderBootstrap.BootResult current = boot;
            if (current == null) {
                return true;
            }
            return current.sessions().playerChatted(
                    sender.getUUID(), sender.getScoreboardName(), message.signedContent());
        });
    }

    private void start(MinecraftServer server) {
        PlatformEnvironment environment = new FabricPlatformEnvironment(server);
        RockConfig config = new TomlConfigEngine(environment.dataDirectory().resolve("config"), System::getenv)
                .loadModuleConfig("rock", DEFAULT_CONFIG);
        DatabaseSettings settings = DatabaseSettings.fromConfig(config, environment.dataDirectory());
        boot = LoaderBootstrap.boot(environment, List.of(new RockDataModule(settings)));
        log.info("ROCK SUITE started on Fabric");
    }

    private void stop() {
        LoaderBootstrap.BootResult current = boot;
        if (current != null) {
            current.platform().close();
            boot = null;
        }
    }
}
