package dev.rock.api.protocol;

import dev.rock.api.service.RockService;
import java.util.UUID;

/**
 * Sends an encoded protocol frame to a connected client (RFC-001). Implemented
 * per delivery channel — the loader custom-payload channel (in-game) and the
 * web dashboard WebSocket. Frames are opaque bytes (the rock-protocol codec is
 * an implementation detail of the projection layer), so a transport never needs
 * to understand the wire model: it just routes bytes to the players it knows and
 * drops the rest. Living in rock-api lets feature modules (e.g. rock-web)
 * contribute a transport without depending on rock-protocol.
 */
public interface ProtocolTransport extends RockService {

    /** Delivers an already-encoded frame to a player, if this transport has them. */
    void send(UUID playerId, byte[] frame);
}
