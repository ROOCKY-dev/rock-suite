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
}
