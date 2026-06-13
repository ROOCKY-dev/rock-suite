package dev.rock.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rock.api.domain.AccountType;
import dev.rock.api.domain.RockEconomyAccount;
import dev.rock.api.domain.owner.PlayerOwner;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.economy.BalanceChangedEvent;
import dev.rock.api.services.PermissionService;
import dev.rock.core.event.DefaultEventBus;
import dev.rock.core.service.DefaultServiceRegistry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** The projection layer is exercised end-to-end with a fake transport (no client needed). */
class ProtocolHubTest {

    private EventBus eventBus;
    private DefaultServiceRegistry registry;
    private ProtocolHub hub;

    // Fake transport: records the decoded messages each player received.
    private final Map<UUID, List<ProtocolMessage>> delivered = new ConcurrentHashMap<>();

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        eventBus = new DefaultEventBus(Executors.newVirtualThreadPerTaskExecutor());
        registry = new DefaultServiceRegistry();
        registry.register(ProtocolTransport.class, (playerId, frame) ->
                delivered.computeIfAbsent(playerId, k -> new java.util.concurrent.CopyOnWriteArrayList<>())
                        .add(ProtocolCodec.decode(frame).orElseThrow()));
        hub = new ProtocolHub(eventBus, registry);
        hub.onEnable();
    }

    private List<ProtocolMessage> inbox(UUID player) {
        return delivered.getOrDefault(player, List.of());
    }

    @Test
    void handshakeNegotiatesVersionAndPermissionFiltersCapabilities() {
        PermissionService permissions = Mockito.mock(PermissionService.class);
        Mockito.when(permissions.has(alice, "rock.client.claims")).thenReturn(true);
        Mockito.when(permissions.has(alice, "rock.client.wallet")).thenReturn(true);
        Mockito.when(permissions.has(alice, "rock.admin.inspector")).thenReturn(false);
        registry.register(PermissionService.class, permissions);

        ProtocolMessage.Welcome welcome = hub.handshake(alice, new ProtocolMessage.Hello(
                5, List.of("CLAIMS", "WALLET", "ADMIN_INSPECTOR", "BOGUS")));

        assertEquals(ProtocolMessage.PROTOCOL_VERSION, welcome.protocolVersion(), "negotiated down to server version");
        assertEquals(List.of("CLAIMS", "WALLET"), welcome.grantedCapabilities(),
                "admin capability denied, unknown capability dropped");
        assertTrue(hub.session(alice).orElseThrow().has(Capability.WALLET));
        assertFalse(hub.session(alice).orElseThrow().has(Capability.ADMIN_INSPECTOR));
    }

    @Test
    void balanceChangeProjectsOnlyToSubscribedOwner() {
        // Alice subscribed to WALLET; Bob did not handshake at all.
        hub.handshake(alice, new ProtocolMessage.Hello(1, List.of("WALLET")));

        RockEconomyAccount aliceAccount = new RockEconomyAccount(
                UUID.randomUUID(), new PlayerOwner(alice), AccountType.PLAYER, new BigDecimal("125.00"), null);
        eventBus.publish(new BalanceChangedEvent(aliceAccount, new BigDecimal("100.00")));

        RockEconomyAccount bobAccount = new RockEconomyAccount(
                UUID.randomUUID(), new PlayerOwner(bob), AccountType.PLAYER, new BigDecimal("5.00"), null);
        eventBus.publish(new BalanceChangedEvent(bobAccount, new BigDecimal("0.00")));

        List<ProtocolMessage> aliceInbox = inbox(alice);
        assertEquals(1, aliceInbox.size());
        ProtocolMessage.Projection p = (ProtocolMessage.Projection) aliceInbox.getFirst();
        assertEquals("wallet.balance", p.type());
        assertEquals("125.00", p.field("balance"));
        assertTrue(inbox(bob).isEmpty(), "Bob never handshook → receives nothing");
    }

    @Test
    void projectionsRespectCapabilityGrants() {
        // Alice holds only WALLET; a CLAIMS projection must not reach her.
        hub.handshake(alice, new ProtocolMessage.Hello(1, List.of("WALLET")));

        dev.rock.api.domain.RockClaim claim = new dev.rock.api.domain.RockClaim(
                UUID.randomUUID(), "Base", new PlayerOwner(alice), dev.rock.api.domain.ClaimType.PLAYER,
                new dev.rock.api.domain.bounds.ChunkBounds(UUID.randomUUID(),
                        java.util.Set.of(new dev.rock.api.domain.bounds.ChunkBounds.ChunkCoordinate(0, 0))),
                java.time.Instant.now(), java.time.Instant.now(), null);
        hub.projectClaimEntered(alice, claim);

        assertTrue(inbox(alice).isEmpty(), "no CLAIMS capability → no claim projection");
    }

    @Test
    void disconnectEndsTheSession() {
        hub.handshake(alice, new ProtocolMessage.Hello(1, List.of("WALLET")));
        assertTrue(hub.session(alice).isPresent());

        hub.disconnect(alice);

        assertTrue(hub.session(alice).isEmpty());
    }

    // --- Inbound: frame ingest + intent dispatch ----------------------------

    @Test
    void receiveHelloRunsHandshakeAndReturnsWelcome() {
        hub.receive(alice, ProtocolCodec.encode(new ProtocolMessage.Hello(1, List.of("WALLET", "CLAIMS"))));

        List<ProtocolMessage> received = inbox(alice);
        assertEquals(1, received.size());
        ProtocolMessage.Welcome welcome = (ProtocolMessage.Welcome) received.getFirst();
        assertEquals(List.of("WALLET", "CLAIMS"), welcome.grantedCapabilities());
        assertTrue(hub.session(alice).isPresent(), "session opened from the inbound Hello frame");
    }

    @Test
    void pingIntentEchoesPongNonce() {
        hub.handshake(alice, new ProtocolMessage.Hello(1, List.of()));

        hub.receive(alice, ProtocolCodec.encode(new ProtocolMessage.Intent("session.ping", Map.of("nonce", "abc123"))));

        ProtocolMessage.Projection pong = (ProtocolMessage.Projection) inbox(alice).getFirst();
        assertEquals("session.pong", pong.type());
        assertEquals("abc123", pong.field("nonce"), "ping needs no capability, only a live session");
    }

    @Test
    void claimsListIntentStreamsOwnedClaimsThenEnd() {
        dev.rock.api.services.ClaimService claims = Mockito.mock(dev.rock.api.services.ClaimService.class);
        dev.rock.api.domain.RockClaim claim = new dev.rock.api.domain.RockClaim(
                UUID.randomUUID(), "Base", new PlayerOwner(alice), dev.rock.api.domain.ClaimType.PLAYER,
                new dev.rock.api.domain.bounds.ChunkBounds(UUID.randomUUID(),
                        java.util.Set.of(new dev.rock.api.domain.bounds.ChunkBounds.ChunkCoordinate(0, 0))),
                java.time.Instant.now(), java.time.Instant.now(), null);
        Mockito.when(claims.findByOwner(new PlayerOwner(alice)))
                .thenReturn(java.util.concurrent.CompletableFuture.completedFuture(List.of(claim)));
        registry.register(dev.rock.api.services.ClaimService.class, claims);
        hub.handshake(alice, new ProtocolMessage.Hello(1, List.of("CLAIMS")));

        hub.receive(alice, ProtocolCodec.encode(new ProtocolMessage.Intent("claims.list", Map.of())));

        List<ProtocolMessage> received = inbox(alice);
        assertEquals(2, received.size(), "one item + an end marker");
        ProtocolMessage.Projection item = (ProtocolMessage.Projection) received.get(0);
        assertEquals("claim.list.item", item.type());
        assertEquals("Base", item.field("name"));
        ProtocolMessage.Projection end = (ProtocolMessage.Projection) received.get(1);
        assertEquals("claim.list.end", end.type());
        assertEquals("1", end.field("count"));
    }

    @Test
    void intentWithoutRequiredCapabilityIsDropped() {
        // Handshake with NO capabilities: claims.list (needs CLAIMS) is refused.
        hub.handshake(alice, new ProtocolMessage.Hello(1, List.of()));

        hub.receive(alice, ProtocolCodec.encode(new ProtocolMessage.Intent("claims.list", Map.of())));

        assertTrue(inbox(alice).isEmpty(), "server-authoritative: missing capability → nothing happens");
    }

    @Test
    void intentBeforeHandshakeIsDropped() {
        hub.receive(bob, ProtocolCodec.encode(new ProtocolMessage.Intent("session.ping", Map.of("nonce", "x"))));

        assertTrue(inbox(bob).isEmpty(), "no session → intent ignored");
    }

    @Test
    void unknownAndClientOnlyFramesAreIgnored() {
        hub.handshake(alice, new ProtocolMessage.Hello(1, List.of("WALLET")));

        // A server→client frame a client should never send, plus an unknown intent.
        hub.receive(alice, ProtocolCodec.encode(new ProtocolMessage.Welcome(1, List.of("WALLET"))));
        hub.receive(alice, ProtocolCodec.encode(new ProtocolMessage.Intent("does.not.exist", Map.of())));

        assertTrue(inbox(alice).isEmpty(), "no echo, no crash");
    }
}
