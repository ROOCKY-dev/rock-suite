package dev.rock.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ProtocolCodecTest {

    private ProtocolMessage roundTrip(ProtocolMessage message) {
        return ProtocolCodec.decode(ProtocolCodec.encode(message)).orElseThrow();
    }

    @Test
    void helloAndWelcomeRoundTrip() {
        var hello = new ProtocolMessage.Hello(1, List.of("CLAIMS", "WALLET"));
        assertEquals(hello, roundTrip(hello));

        var welcome = new ProtocolMessage.Welcome(1, List.of("CLAIMS"));
        assertEquals(welcome, roundTrip(welcome));
    }

    @Test
    void projectionAndIntentRoundTripPreservingFields() {
        var projection = new ProtocolMessage.Projection("claim.entered",
                Map.of("id", "abc", "name", "Alice's Base", "owner", "PLAYER:xyz"));
        ProtocolMessage decoded = roundTrip(projection);
        assertInstanceOf(ProtocolMessage.Projection.class, decoded);
        assertEquals("Alice's Base", ((ProtocolMessage.Projection) decoded).field("name"));

        var intent = new ProtocolMessage.Intent("claim.create", Map.of("name", "NewTown"));
        assertEquals(intent, roundTrip(intent));
    }

    @Test
    void unknownMessageKindDecodesToEmpty() {
        byte[] frame = ProtocolCodec.encode(new ProtocolMessage.Hello(1, List.of()));
        frame[0] = 99; // corrupt the kind tag to a future/unknown type

        assertTrue(ProtocolCodec.decode(frame).isEmpty(), "forward-compatible: unknown kinds drop, not throw");
    }

    @Test
    void truncatedFrameDecodesToEmptyNeverThrows() {
        byte[] frame = ProtocolCodec.encode(
                new ProtocolMessage.Projection("x", Map.of("a", "b")));
        byte[] truncated = java.util.Arrays.copyOf(frame, 3);

        assertTrue(ProtocolCodec.decode(truncated).isEmpty());
    }
}
