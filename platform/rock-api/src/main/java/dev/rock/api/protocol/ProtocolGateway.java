package dev.rock.api.protocol;

import dev.rock.api.service.RockService;
import java.util.UUID;

/**
 * The server-side projection gateway (RFC-001), as seen from outside the
 * projection layer. Delivery channels register a {@link ProtocolTransport} and
 * feed inbound client frames back in; the implementation (rock-protocol's
 * ProtocolHub) handles handshake, capability gating, and per-player projection.
 *
 * <p>Exposed through rock-api so a feature module can contribute a transport
 * (the web dashboard's WebSocket feed) and resolve the gateway via the
 * {@code ServiceRegistry} without depending on rock-protocol.
 */
public interface ProtocolGateway extends RockService {

    /** Registers a delivery transport (idempotent). */
    void addTransport(ProtocolTransport transport);

    /** Removes a previously registered transport. */
    void removeTransport(ProtocolTransport transport);

    /** Feeds an inbound frame from a client into the projection layer. */
    void receive(UUID playerId, byte[] frame);
}
