package dev.rock.loader.neoforge.real;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * The {@code rock:protocol} custom-payload envelope (NeoForge side) — byte-for-
 * byte identical to the Fabric one: a raw ProtocolCodec frame carried as the
 * rest of the packet buffer (no inner length prefix). Proof the wire model is
 * loader-agnostic; only the registration/transport plumbing differs per loader.
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
