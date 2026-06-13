package dev.rock.loader.fabric.real;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.rock.api.command.CommandResult;
import dev.rock.api.command.CommandService;
import dev.rock.api.config.RockConfig;
import dev.rock.api.events.world.BlockChangeType;
import dev.rock.core.bootstrap.PlatformEnvironment;
import dev.rock.core.command.AliasConfig;
import dev.rock.core.config.TomlConfigEngine;
import dev.rock.core.loader.LoaderBootstrap;
import dev.rock.data.DatabaseSettings;
import dev.rock.data.RockDataModule;
import dev.rock.protocol.ProtocolHub;
import dev.rock.protocol.RockProtocolModule;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The real Fabric adapter (K3): boots the ROCK platform, maps joins/leaves,
 * chat, and block breaks onto the platform event layer, and registers the
 * /rock command tree plus short aliases with brigadier.
 */
public final class RockFabricMod implements DedicatedServerModInitializer {

    static final String DEFAULT_CONFIG = """
            # ROCK SUITE platform configuration
            [database]
            # url = "jdbc:postgresql://localhost:5432/rock"

            [database.pool]
            maximum-pool-size = 10
            minimum-idle = 2

            [aliases]
            enabled = true
            # Defaults cover /ban /mute /home /pay /balance /r etc.
            # Disable one to dodge a collision:  home = false
            # Add your own:                       mywarp = ["warp"]
            """;

    private static final Logger log = LoggerFactory.getLogger(RockFabricMod.class);

    private volatile LoaderBootstrap.BootResult boot;

    @Override
    public void onInitializeServer() {
        ServerLifecycleEvents.SERVER_STARTED.register(this::start);
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> stop());

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            LoaderBootstrap.BootResult current = boot;
            if (current == null) {
                return;
            }
            ServerPlayer player = handler.player;
            // Ban gate (rock-moderation) before the session is recorded.
            Optional<String> denial = current.sessions().joinDenialReason(player.getUUID());
            if (denial.isPresent()) {
                handler.disconnect(Component.literal(denial.get()));
                return;
            }
            current.sessions().playerJoined(player.getUUID(), player.getScoreboardName());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            LoaderBootstrap.BootResult current = boot;
            if (current != null) {
                ServerPlayer player = handler.player;
                current.sessions().playerLeft(player.getUUID(), player.getScoreboardName());
            }
        });

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            LoaderBootstrap.BootResult current = boot;
            if (current == null) {
                return true;
            }
            UUID worldId = current.worldEvents().worldId(world.dimension().identifier().toString());
            String blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            return current.worldEvents().blockChange(
                    player.getUUID(), false, worldId, pos.getX(), pos.getY(), pos.getZ(),
                    BlockChangeType.BREAK, blockId, "minecraft:air");
        });

        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            LoaderBootstrap.BootResult current = boot;
            if (current == null) {
                return true;
            }
            return current.sessions().playerChatted(
                    sender.getUUID(), sender.getScoreboardName(), message.signedContent());
        });

        // Brigadier roots are static (registered before the platform boots), so
        // register /rock plus the full universe of default alias names; whether
        // each alias actually does anything is governed by config at dispatch
        // time (AliasConfig, applied in start()).
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            dispatcher.register(rockCommand("rock"));
            for (String alias : AliasConfig.DEFAULTS.keySet()) {
                if (!alias.equals("rock")) {
                    dispatcher.register(rockCommand(alias));
                }
            }
        });

        // rock-protocol on the wire: register the rock:protocol custom-payload
        // channel both ways and route inbound frames into the ProtocolHub. The
        // hub (a singleton in RockProtocolModule) and the outbound transport are
        // wired in start(); registration here is init-time as Fabric requires.
        PayloadTypeRegistry.playC2S().register(RockProtocolPayload.TYPE, RockProtocolPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RockProtocolPayload.TYPE, RockProtocolPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RockProtocolPayload.TYPE, (payload, context) -> {
            LoaderBootstrap.BootResult current = boot;
            if (current == null) {
                return;
            }
            ProtocolHub hub = current.platform().injector().getInstance(ProtocolHub.class);
            hub.receive(context.player().getUUID(), payload.data());
        });
    }

    private LiteralArgumentBuilder<CommandSourceStack> rockCommand(String literal) {
        return Commands.literal(literal)
                .executes(ctx -> execute(ctx.getSource(), literal, List.of()))
                .then(Commands.argument("args", StringArgumentType.greedyString())
                        .executes(ctx -> execute(ctx.getSource(), literal,
                                Arrays.asList(StringArgumentType.getString(ctx, "args").trim().split("\\s+")))));
    }

    private int execute(CommandSourceStack source, String literal, List<String> args) {
        LoaderBootstrap.BootResult current = boot;
        if (current == null) {
            source.sendSystemMessage(Component.literal("ROCK is still starting…"));
            return 0;
        }
        CommandService commands = current.platform().services().require(CommandService.class);
        FabricSender sender = new FabricSender(source, current);
        CommandResult result;
        if (literal.equals("rock")) {
            result = commands.dispatch(sender, args);
        } else if (commands.aliases().containsKey(literal)) {
            result = commands.dispatchAlias(sender, literal, args);
        } else {
            sender.sendMessage("That alias is disabled. Use /rock …");
            return 0;
        }
        return result == CommandResult.SUCCESS ? 1 : 0;
    }

    private void start(MinecraftServer server) {
        PlatformEnvironment environment = new FabricEnvironment(server);
        RockConfig config = new TomlConfigEngine(environment.dataDirectory().resolve("config"), System::getenv)
                .loadModuleConfig("rock", DEFAULT_CONFIG);
        DatabaseSettings settings = DatabaseSettings.fromConfig(config, environment.dataDirectory());
        boot = LoaderBootstrap.boot(environment,
                List.of(new RockDataModule(settings), new RockProtocolModule()));
        // Config-driven aliases: resolve the [aliases] table over the defaults
        // and register them into CommandService (the brigadier roots are
        // already live and route through dispatchAlias).
        AliasConfig.apply(config, boot.platform().services().require(CommandService.class));
        // Give the (already-enabled) ProtocolHub a live transport so projections
        // reach connected clients over the rock:protocol channel.
        boot.platform().injector().getInstance(ProtocolHub.class)
                .addTransport(new FabricProtocolTransport(server));
        log.info("ROCK SUITE started on Fabric ({} — real adapter)", server.getServerVersion());
    }

    private void stop() {
        LoaderBootstrap.BootResult current = boot;
        if (current != null) {
            current.platform().close();
            boot = null;
        }
    }
}
