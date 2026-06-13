package dev.rock.loader.fabric.real;

import dev.rock.api.protocol.ProtocolTransport;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Fabric end of {@link ProtocolTransport}: delivers an encoded frame to a
 * player as a {@code rock:protocol} custom payload. Frames for an offline player
 * are dropped silently (per the SPI contract). Netty handles the cross-thread
 * send, so projections produced on async service threads deliver safely.
 */
final class FabricProtocolTransport implements ProtocolTransport {

    private final MinecraftServer server;

    FabricProtocolTransport(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void send(UUID playerId, byte[] frame) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            ServerPlayNetworking.send(player, new RockProtocolPayload(frame));
        }
    }
}
