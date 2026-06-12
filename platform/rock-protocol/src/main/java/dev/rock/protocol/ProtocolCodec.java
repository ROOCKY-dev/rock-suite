package dev.rock.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Zero-dependency binary codec for {@link ProtocolMessage} (RFC-001). Wire
 * format is a tagged, length-prefixed envelope so it rides equally on a Fabric
 * custom-payload packet or a WebSocket binary frame:
 *
 * <pre>
 *   u8  messageKind   (0 hello, 1 welcome, 2 projection, 3 intent)
 *   ... kind-specific body, all strings as UTF length-prefixed
 * </pre>
 *
 * Decoding is forward-compatible: an unknown {@code messageKind} decodes to an
 * empty {@link Optional} (dropped, not fatal), so newer servers and older
 * clients interoperate.
 */
public final class ProtocolCodec {

    private static final int KIND_HELLO = 0;
    private static final int KIND_WELCOME = 1;
    private static final int KIND_PROJECTION = 2;
    private static final int KIND_INTENT = 3;

    private ProtocolCodec() {
    }

    public static byte[] encode(ProtocolMessage message) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            switch (message) {
                case ProtocolMessage.Hello hello -> {
                    out.writeByte(KIND_HELLO);
                    out.writeInt(hello.protocolVersion());
                    writeStrings(out, hello.capabilities());
                }
                case ProtocolMessage.Welcome welcome -> {
                    out.writeByte(KIND_WELCOME);
                    out.writeInt(welcome.protocolVersion());
                    writeStrings(out, welcome.grantedCapabilities());
                }
                case ProtocolMessage.Projection projection -> {
                    out.writeByte(KIND_PROJECTION);
                    out.writeUTF(projection.type());
                    writeMap(out, projection.fields());
                }
                case ProtocolMessage.Intent intent -> {
                    out.writeByte(KIND_INTENT);
                    out.writeUTF(intent.type());
                    writeMap(out, intent.fields());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static Optional<ProtocolMessage> decode(byte[] data) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            int kind = in.readUnsignedByte();
            return switch (kind) {
                case KIND_HELLO -> Optional.of(
                        new ProtocolMessage.Hello(in.readInt(), readStrings(in)));
                case KIND_WELCOME -> Optional.of(
                        new ProtocolMessage.Welcome(in.readInt(), readStrings(in)));
                case KIND_PROJECTION -> Optional.of(
                        new ProtocolMessage.Projection(in.readUTF(), readMap(in)));
                case KIND_INTENT -> Optional.of(
                        new ProtocolMessage.Intent(in.readUTF(), readMap(in)));
                default -> Optional.empty(); // unknown kind — forward-compatible drop
            };
        } catch (IOException e) {
            return Optional.empty(); // truncated/garbage frame — never throw on the wire
        }
    }

    private static void writeStrings(DataOutputStream out, List<String> values) throws IOException {
        out.writeInt(values.size());
        for (String value : values) {
            out.writeUTF(value);
        }
    }

    private static List<String> readStrings(DataInputStream in) throws IOException {
        int count = in.readInt();
        List<String> values = new ArrayList<>(Math.max(0, count));
        for (int i = 0; i < count; i++) {
            values.add(in.readUTF());
        }
        return values;
    }

    private static void writeMap(DataOutputStream out, Map<String, String> fields) throws IOException {
        out.writeInt(fields.size());
        for (Map.Entry<String, String> entry : fields.entrySet()) {
            out.writeUTF(entry.getKey());
            out.writeUTF(entry.getValue());
        }
    }

    private static Map<String, String> readMap(DataInputStream in) throws IOException {
        int count = in.readInt();
        Map<String, String> fields = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            fields.put(in.readUTF(), in.readUTF());
        }
        return fields;
    }
}
