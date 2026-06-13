package dev.rock.loader.neoforge.real;

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
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The real NeoForge adapter (K4): boots the ROCK platform and maps NeoForge bus
 * events — joins/leaves, chat, block break/place — onto the platform event
 * layer, then registers the /rock command tree plus short aliases with
 * brigadier. The ONLY NeoForge-aware ROCK component on this loader (AVD Rule
 * Zero). Deliberately a line-for-line mirror of the Fabric adapter's behaviour:
 * the platform below the loader seam is byte-identical across both loaders.
 */
@Mod("rock_suite")
public final class RockNeoForgeMod {

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

    private static final Logger log = LoggerFactory.getLogger(RockNeoForgeMod.class);

    private volatile LoaderBootstrap.BootResult boot;

    public RockNeoForgeMod() {
        NeoForge.EVENT_BUS.addListener((Consumer<ServerStartedEvent>) this::start);
        NeoForge.EVENT_BUS.addListener((Consumer<ServerStoppingEvent>) event -> stop());

        NeoForge.EVENT_BUS.addListener((Consumer<PlayerEvent.PlayerLoggedInEvent>) event -> {
            LoaderBootstrap.BootResult current = boot;
            if (current == null) {
                return;
            }
            Player player = event.getEntity();
            // Ban gate (rock-moderation): kick immediately on join if denied.
            Optional<String> denial = current.sessions().joinDenialReason(player.getUUID());
            if (denial.isPresent() && player instanceof ServerPlayer sp) {
                sp.connection.disconnect(Component.literal(denial.get()));
                return;
            }
            current.sessions().playerJoined(player.getUUID(), player.getScoreboardName());
        });
        NeoForge.EVENT_BUS.addListener((Consumer<PlayerEvent.PlayerLoggedOutEvent>) event -> {
            LoaderBootstrap.BootResult current = boot;
            if (current != null) {
                Player player = event.getEntity();
                current.sessions().playerLeft(player.getUUID(), player.getScoreboardName());
            }
        });

        NeoForge.EVENT_BUS.addListener((Consumer<BlockEvent.BreakEvent>) event -> {
            LoaderBootstrap.BootResult current = boot;
            if (current == null) {
                return;
            }
            UUID worldId = current.worldEvents().worldId(dimensionId(event.getLevel()));
            String blockId = BuiltInRegistries.BLOCK.getKey(event.getState().getBlock()).toString();
            boolean allowed = current.worldEvents().blockChange(
                    event.getPlayer().getUUID(), false, worldId,
                    event.getPos().getX(), event.getPos().getY(), event.getPos().getZ(),
                    BlockChangeType.BREAK, blockId, "minecraft:air");
            if (!allowed) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((Consumer<BlockEvent.EntityPlaceEvent>) event -> {
            LoaderBootstrap.BootResult current = boot;
            if (current == null) {
                return;
            }
            Entity placer = event.getEntity();
            UUID placerId = placer instanceof Player p ? p.getUUID() : null;
            UUID worldId = current.worldEvents().worldId(dimensionId(event.getLevel()));
            String placedId = BuiltInRegistries.BLOCK.getKey(event.getPlacedBlock().getBlock()).toString();
            boolean allowed = current.worldEvents().blockChange(
                    placerId, false, worldId,
                    event.getPos().getX(), event.getPos().getY(), event.getPos().getZ(),
                    BlockChangeType.PLACE, "minecraft:air", placedId);
            if (!allowed) {
                event.setCanceled(true);
            }
        });

        NeoForge.EVENT_BUS.addListener((Consumer<ServerChatEvent>) event -> {
            LoaderBootstrap.BootResult current = boot;
            if (current == null) {
                return;
            }
            ServerPlayer player = event.getPlayer();
            boolean allowed = current.sessions().playerChatted(
                    player.getUUID(), player.getScoreboardName(), event.getRawText());
            if (!allowed) {
                event.setCanceled(true);
            }
        });

        // Brigadier roots are registered per server start (RegisterCommandsEvent).
        // Register /rock plus the full universe of default alias names; whether
        // each alias actually does anything is governed by config at dispatch
        // time (AliasConfig, applied in start()).
        NeoForge.EVENT_BUS.addListener((Consumer<RegisterCommandsEvent>) event -> {
            event.getDispatcher().register(rockCommand("rock"));
            for (String alias : AliasConfig.DEFAULTS.keySet()) {
                if (!alias.equals("rock")) {
                    event.getDispatcher().register(rockCommand(alias));
                }
            }
        });
    }

    private static String dimensionId(net.minecraft.world.level.LevelAccessor level) {
        return ((Level) level).dimension().identifier().toString();
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
        NeoForgeSender sender = new NeoForgeSender(source, current);
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

    private void start(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        PlatformEnvironment environment = new NeoForgeEnvironment(server);
        RockConfig config = new TomlConfigEngine(environment.dataDirectory().resolve("config"), System::getenv)
                .loadModuleConfig("rock", DEFAULT_CONFIG);
        DatabaseSettings settings = DatabaseSettings.fromConfig(config, environment.dataDirectory());
        boot = LoaderBootstrap.boot(environment, List.of(new RockDataModule(settings)));
        // Config-driven aliases: resolve the [aliases] table over the defaults
        // and register them into CommandService (the brigadier roots route
        // through dispatchAlias).
        AliasConfig.apply(config, boot.platform().services().require(CommandService.class));
        log.info("ROCK SUITE started on NeoForge ({} — real adapter)", server.getServerVersion());
    }

    private void stop() {
        LoaderBootstrap.BootResult current = boot;
        if (current != null) {
            current.platform().close();
            boot = null;
        }
    }
}
