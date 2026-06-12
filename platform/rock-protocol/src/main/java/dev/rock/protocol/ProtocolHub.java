package dev.rock.protocol;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.domain.RockClaim;
import dev.rock.api.event.EventBus;
import dev.rock.api.event.EventPriority;
import dev.rock.api.event.Subscription;
import dev.rock.api.events.economy.BalanceChangedEvent;
import dev.rock.api.events.player.PlayerLeaveEvent;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.service.RockService;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.api.services.PermissionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The server-side projection layer (RFC-001): "EventBus over the wire, filtered
 * per player". Maintains a {@link ClientSession} per connected protocol client,
 * negotiates capabilities against permissions, subscribes to domain events, and
 * pushes permission-filtered {@link ProtocolMessage.Projection}s through the
 * registered {@link ProtocolTransport}.
 *
 * <p>Fully testable without a real client — the testbench registers a fake
 * transport and asserts the frames a player receives.
 */
@RockInternal
@Singleton
public final class ProtocolHub implements RockService, LifecycleAware {

    private static final Logger log = LoggerFactory.getLogger(ProtocolHub.class);

    private final EventBus eventBus;
    private final ServiceRegistry services;
    private final Map<UUID, ClientSession> sessions = new ConcurrentHashMap<>();
    private final List<Subscription> subscriptions = new ArrayList<>();

    @Inject
    public ProtocolHub(EventBus eventBus, ServiceRegistry services) {
        this.eventBus = eventBus;
        this.services = services;
    }

    // --- Handshake ----------------------------------------------------------

    /**
     * Processes a client {@link ProtocolMessage.Hello}: negotiates the protocol
     * version, grants the subset of requested capabilities the player's
     * permissions allow, registers the session, and returns the
     * {@link ProtocolMessage.Welcome} to send back.
     */
    public ProtocolMessage.Welcome handshake(UUID playerId, ProtocolMessage.Hello hello) {
        int version = Math.min(hello.protocolVersion(), ProtocolMessage.PROTOCOL_VERSION);
        ClientSession session = new ClientSession(playerId, version);
        Optional<PermissionService> permissions = services.find(PermissionService.class);
        List<String> granted = new ArrayList<>();
        for (String requested : hello.capabilities()) {
            Capability capability = Capability.fromWire(requested);
            if (capability == null) {
                continue;
            }
            boolean allowed = permissions
                    .map(p -> p.has(playerId, capability.permission()))
                    .orElse(true); // no permission system → allow (single-player/dev)
            if (allowed) {
                session.grant(capability);
                granted.add(capability.name());
            }
        }
        sessions.put(playerId, session);
        log.debug("Protocol handshake {} → v{} caps {}", playerId, version, granted);
        return new ProtocolMessage.Welcome(version, granted);
    }

    public void disconnect(UUID playerId) {
        sessions.remove(playerId);
    }

    public Optional<ClientSession> session(UUID playerId) {
        return Optional.ofNullable(sessions.get(playerId));
    }

    // --- Outbound projection ------------------------------------------------

    /** Sends a projection to one player iff they hold the capability. */
    public void project(UUID playerId, Capability capability, ProtocolMessage.Projection projection) {
        ClientSession session = sessions.get(playerId);
        if (session == null || !session.has(capability)) {
            return;
        }
        services.find(ProtocolTransport.class)
                .ifPresent(transport -> transport.send(playerId, ProtocolCodec.encode(projection)));
    }

    /**
     * Pushes a claim boundary/entry projection — called by the loader adapter's
     * movement hook when a player crosses into a claim (the in-world boundary
     * overlay + entry toast, RFC-001 Tier 1).
     */
    public void projectClaimEntered(UUID playerId, RockClaim claim) {
        project(playerId, Capability.CLAIMS, new ProtocolMessage.Projection("claim.entered", Map.of(
                "id", claim.id().toString(),
                "name", claim.displayName(),
                "owner", claim.owner().serialize(),
                "type", claim.type().name())));
    }

    // --- Lifecycle: subscribe to domain events ------------------------------

    @Override
    public void onEnable() {
        subscriptions.add(eventBus.subscribe(BalanceChangedEvent.class, EventPriority.LAST, event -> {
            if (event.account().owner() instanceof dev.rock.api.domain.owner.PlayerOwner owner) {
                project(owner.id(), Capability.WALLET, new ProtocolMessage.Projection("wallet.balance", Map.of(
                        "balance", event.account().balance().toPlainString(),
                        "previous", event.previousBalance().toPlainString())));
            }
        }));
        subscriptions.add(eventBus.subscribe(PlayerLeaveEvent.class, EventPriority.LAST,
                event -> disconnect(event.player().id())));
        log.info("Protocol hub active (projection layer for rock-client / rock-web)");
    }

    @Override
    public void onDisable() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
        sessions.clear();
    }
}
