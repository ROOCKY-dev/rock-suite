package dev.rock.protocol;

import java.util.Map;

/**
 * The rock-protocol wire model (RFC-001): server↔client payloads shared by the
 * loader custom-payload channel and the web dashboard WebSocket feed.
 *
 * <p>Server-authoritative: the client renders {@link Projection}s the server
 * pushes and submits {@link Intent}s the server validates with the same
 * permission checks a command would. A modified client gains nothing.
 *
 * <p>Messages are deliberately schema-light — a type tag plus string fields —
 * so the {@link ProtocolCodec} stays zero-dependency and version-tolerant
 * (unknown fields ignored, unknown types dropped).
 */
public sealed interface ProtocolMessage permits ProtocolMessage.Hello, ProtocolMessage.Welcome,
        ProtocolMessage.Projection, ProtocolMessage.Intent {

    /** Stable wire tag, e.g. {@code "hello"}, {@code "claim.entered"}. */
    String type();

    /** Protocol version this codec/model speaks. Bumped on breaking changes. */
    int PROTOCOL_VERSION = 1;

    // --- Handshake ----------------------------------------------------------

    /** Client → server: announces protocol version + desired capabilities. */
    record Hello(int protocolVersion, java.util.List<String> capabilities) implements ProtocolMessage {
        @Override
        public String type() {
            return "hello";
        }
    }

    /** Server → client: negotiated protocol version + the capabilities granted. */
    record Welcome(int protocolVersion, java.util.List<String> grantedCapabilities) implements ProtocolMessage {
        @Override
        public String type() {
            return "welcome";
        }
    }

    // --- Server → client projections ---------------------------------------

    /**
     * A permission-filtered data push for a capability the client subscribed to
     * (claim boundaries, wallet, tpa toast, …). Fields are capability-specific.
     */
    record Projection(String type, Map<String, String> fields) implements ProtocolMessage {
        public Projection {
            fields = Map.copyOf(fields);
        }

        public String field(String key) {
            return fields.get(key);
        }
    }

    // --- Client → server intents -------------------------------------------

    /** A client request the server validates and turns into the matching service call. */
    record Intent(String type, Map<String, String> fields) implements ProtocolMessage {
        public Intent {
            fields = Map.copyOf(fields);
        }

        public String field(String key) {
            return fields.get(key);
        }
    }
}
