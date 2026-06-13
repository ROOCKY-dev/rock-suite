package dev.rock.loader.fabric.real;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The {@code rock:protocol} custom-payload envelope: a raw rock-protocol frame
 * (already encoded by ProtocolCodec) carried over Minecraft's plugin-message
 * channel in both directions. The platform's wire model stays loader-agnostic;
 * this record is the only Fabric-networking-aware wrapper around it.
 */
public record RockProtocolPayload(byte[] data) implements CustomPacketPayload {

    // createType(String) parses its argument as a path under "minecraft", which
    // rejects the ':' — build the id with an explicit namespace instead.
    public static final Type<RockProtocolPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath("rock", "protocol"));

    // The custom-payload body is "the rest of the packet buffer" — no inner
    // length prefix (writeByteArray would add a VarInt the vanilla framing
    // doesn't expect). Read/write the raw remaining bytes.
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
