package dev.rock.protocol;

import dev.rock.api.service.RockService;
import java.util.UUID;

/**
 * Sends encoded protocol messages to a connected client (RFC-001). Implemented
 * by the loader (custom-payload channel) and, later, the web dashboard
 * (WebSocket). The hub holds exactly one transport; messages it cannot deliver
 * (player offline) are dropped silently.
 */
public interface ProtocolTransport extends RockService {

    /** Delivers an already-encoded frame to a player, if connected. */
    void send(UUID playerId, byte[] frame);
}
