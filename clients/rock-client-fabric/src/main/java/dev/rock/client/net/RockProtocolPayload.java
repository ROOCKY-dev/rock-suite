package dev.rock.client.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client end of the {@code rock:protocol} custom-payload envelope — byte-for-byte
 * the server's. Body is the raw ProtocolCodec frame as the rest of the buffer.
 */
public record RockProtocolPayload(byte[] data) implements CustomPacketPayload {

    public static final Type<RockProtocolPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("rock", "protocol"));

    public static final StreamCodec<FriendlyByteBuf, RockProtocolPayload> CODEC =
            CustomPacketPayload.codec(
                    (payload, buf) -> buf.writeBytes(payload.data()),
                    buf -> {
                        byte[] frame = new byte[buf.readableBytes()];
                        buf.readBytes(frame);
                        return new RockProtocolPayload(frame);
                    });

    @Override
    public Type<RockProtocolPayload> type() {
        return TYPE;
    }
}
