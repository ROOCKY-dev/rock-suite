package dev.rock.loader.neoforge.real;

import dev.rock.api.command.CommandSender;
import dev.rock.api.domain.RockLocation;
import dev.rock.api.services.PermissionService;
import dev.rock.core.loader.LoaderBootstrap;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * CommandSender over a brigadier source. Permission checks route through
 * rock-permissions; the console always passes; server operators keep a safety
 * bypass. Mirror of the Fabric adapter's FabricSender — proof that the platform
 * sees an identical CommandSender contract regardless of loader.
 */
final class NeoForgeSender implements CommandSender {

    private final CommandSourceStack source;
    private final LoaderBootstrap.BootResult boot;

    NeoForgeSender(CommandSourceStack source, LoaderBootstrap.BootResult boot) {
        this.source = source;
        this.boot = boot;
    }

    private ServerPlayer player() {
        return source.getPlayer();
    }

    @Override
    public UUID playerId() {
        ServerPlayer player = player();
        return player == null ? null : player.getUUID();
    }

    @Override
    public String name() {
        return source.getTextName();
    }

    @Override
    public RockLocation location() {
        ServerPlayer player = player();
        if (player == null) {
            return null;
        }
        UUID worldId = boot.worldEvents().worldId(player.level().dimension().identifier().toString());
        return new RockLocation(worldId, player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
    }

    @Override
    public void sendMessage(String message) {
        source.sendSystemMessage(Component.literal(message));
    }

    @Override
    public boolean hasPermission(String node) {
        ServerPlayer player = player();
        if (player == null) {
            return true; // console
        }
        boolean granted = boot.platform().services().find(PermissionService.class)
                .map(permissions -> permissions.has(player.getUUID(), node))
                .orElse(false);
        // Op safety bypass (1.21.11 PermissionSet model: ADMIN ≈ old level 3).
        return granted || source.permissions().hasPermission(
                net.minecraft.server.permissions.Permissions.COMMANDS_ADMIN);
    }
}
