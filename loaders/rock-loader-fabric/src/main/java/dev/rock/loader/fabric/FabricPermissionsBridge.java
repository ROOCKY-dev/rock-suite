package dev.rock.loader.fabric;

import dev.rock.api.services.PermissionService;
import dev.rock.core.loader.LoaderBootstrap;
import java.util.function.Supplier;
import me.lucko.fabric.api.permissions.v0.PermissionCheckEvent;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Platform keystone K2: answers fabric-permissions-api checks from
 * rock-permissions, so every mod on the server that uses the de-facto
 * permission API routes through ROCK with zero integration work.
 *
 * <p>Kept in its own class so the registration can be skipped gracefully when
 * fabric-permissions-api is not installed (NoClassDefFoundError guard in
 * {@link RockFabricMod}).
 */
final class FabricPermissionsBridge {

    private static final Logger log = LoggerFactory.getLogger(FabricPermissionsBridge.class);

    private FabricPermissionsBridge() {
    }

    static void register(Supplier<LoaderBootstrap.BootResult> boot) {
        PermissionCheckEvent.EVENT.register((source, permission) -> {
            LoaderBootstrap.BootResult current = boot.get();
            if (current == null || !(source instanceof CommandSourceStack stack)) {
                return TriState.DEFAULT;
            }
            ServerPlayer player = stack.getPlayer();
            if (player == null) {
                return TriState.DEFAULT;
            }
            return current.platform().services().find(PermissionService.class)
                    .map(permissions -> switch (permissions.check(player.getUUID(), permission)) {
                        case ALLOW -> TriState.TRUE;
                        case DENY -> TriState.FALSE;
                        case UNSET -> TriState.DEFAULT;
                    })
                    .orElse(TriState.DEFAULT);
        });
        log.info("fabric-permissions-api provider registered — mods' permission checks route to ROCK");
    }
}
