package dev.rock.loader.fabric.real;

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
 * rock-permissions; the console always passes; server operators (level 3+)
 * keep a safety bypass.
 */
final class FabricSender implements CommandSender {

    private final CommandSourceStack source;
    private final LoaderBootstrap.BootResult boot;

    FabricSender(CommandSourceStack source, LoaderBootstrap.BootResult boot) {
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
        UUID worldId = boot.worldEvents().worldId(player.level().dimension().location().toString());
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
        // 1.20.x delta: the int-level permission model (PermissionSet arrives in
        // 1.21.11). Op safety bypass = command level 2.
        return granted || source.hasPermission(2);
    }
}
