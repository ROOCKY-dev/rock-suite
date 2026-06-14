package dev.rock.client.net;

import dev.rock.protocol.ProtocolCodec;
import dev.rock.protocol.ProtocolMessage;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * The client end of rock-protocol (RFC-001 net/): registers the rock:protocol
 * channel, handshakes on join, and turns inbound projections into render state
 * the HUD/screens read. Server-authoritative — the client only renders what the
 * server pushes and submits intents the server re-validates.
 */
public final class RockClientProtocol {

    /** A claim as projected to the client (read-only view). */
    public record Claim(String id, String name, String type) {
    }

    private volatile String walletBalance;
    private final List<Claim> claims = new CopyOnWriteArrayList<>();
    private volatile String toast;
    private volatile long toastAt;

    public void register() {
        // Handshake on join: advertise our version + desired capabilities.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                send(new ProtocolMessage.Hello(ProtocolMessage.PROTOCOL_VERSION,
                        List.of("CLAIMS", "WALLET", "TPA"))));

        ClientPlayNetworking.registerGlobalReceiver(RockProtocolPayload.TYPE, (payload, context) ->
                ProtocolCodec.decode(payload.data()).ifPresent(message ->
                        context.client().execute(() -> onMessage(message))));
    }

    private void onMessage(ProtocolMessage message) {
        if (message instanceof ProtocolMessage.Projection projection) {
            onProjection(projection);
        }
        // Welcome is informational; intents/hello are never inbound here.
    }

    private void onProjection(ProtocolMessage.Projection p) {
        switch (p.type()) {
            case "wallet.balance" -> {
                walletBalance = p.field("balance");
                toast("Balance: " + walletBalance);
            }
            case "claim.entered" -> toast("Entering " + p.field("name"));
            case "claim.list.item" -> claims.add(new Claim(p.field("id"), p.field("name"), p.field("type")));
            default -> {
                // claim.list.end / unknown — ignored
            }
        }
    }

    /** Ask the server for this player's claims (populates {@link #claims()}). */
    public void requestClaims() {
        claims.clear();
        send(new ProtocolMessage.Intent("claims.list", Map.of()));
    }

    public void send(ProtocolMessage message) {
        if (ClientPlayNetworking.canSend(RockProtocolPayload.TYPE)) {
            ClientPlayNetworking.send(new RockProtocolPayload(ProtocolCodec.encode(message)));
        }
    }

    public String walletBalance() {
        return walletBalance;
    }

    public List<Claim> claims() {
        return claims;
    }

    private void toast(String text) {
        toast = text;
        toastAt = System.currentTimeMillis();
    }

    /** The current toast, or null once it has aged out (~4s). */
    public String activeToast() {
        return (System.currentTimeMillis() - toastAt < 4000) ? toast : null;
    }
}
