package dev.rock.loader.neoforge.real;

import dev.rock.protocol.ProtocolTransport;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The NeoForge end of {@link ProtocolTransport}: delivers an encoded frame to a
 * player as a {@code rock:protocol} custom payload via the PacketDistributor.
 * Frames for an offline player are dropped silently (SPI contract).
 */
final class NeoForgeProtocolTransport implements ProtocolTransport {

    private final MinecraftServer server;

    NeoForgeProtocolTransport(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public void send(UUID playerId, byte[] frame) {
        ServerPlayer player = server.getPlayerList().getPlayer(playerId);
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new RockProtocolPayload(frame));
        }
    }
}
