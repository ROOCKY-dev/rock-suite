package dev.rock.protocol;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.domain.RockClaim;
import dev.rock.api.domain.owner.PlayerOwner;
import dev.rock.api.event.EventBus;
import dev.rock.api.event.EventPriority;
import dev.rock.api.event.Subscription;
import dev.rock.api.events.economy.BalanceChangedEvent;
import dev.rock.api.events.player.PlayerLeaveEvent;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.service.RockService;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.api.services.ClaimService;
import dev.rock.api.services.PermissionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
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
    private final Map<String, IntentHandler> intentHandlers = new ConcurrentHashMap<>();

    @Inject
    public ProtocolHub(EventBus eventBus, ServiceRegistry services) {
        this.eventBus = eventBus;
        this.services = services;
    }

    /**
     * A server-side intent handler plus the capability a client must hold to
     * invoke it. A {@code null} capability means the intent only requires a live
     * session (e.g. a keepalive ping).
     */
    public record IntentHandler(Capability capability, BiConsumer<UUID, ProtocolMessage.Intent> action) {
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

    // --- Inbound: frame ingest + intent dispatch ----------------------------

    /**
     * The transport's single entry point for an inbound frame from a client.
     * Decodes it and routes: a {@link ProtocolMessage.Hello} runs the handshake
     * and sends back the {@link ProtocolMessage.Welcome}; an
     * {@link ProtocolMessage.Intent} is validated and dispatched. Server-bound
     * frames a client should never send (Welcome/Projection) are ignored.
     * Garbage decodes to empty and is dropped — the wire never throws.
     */
    public void receive(UUID playerId, byte[] frame) {
        ProtocolCodec.decode(frame).ifPresent(message -> {
            switch (message) {
                case ProtocolMessage.Hello hello -> deliver(playerId, handshake(playerId, hello));
                case ProtocolMessage.Intent intent -> handleIntent(playerId, intent);
                default -> log.debug("Ignoring client-sent {} from {}", message.type(), playerId);
            }
        });
    }

    /**
     * Registers an intent handler. The loader/web layer and modules use this to
     * extend the client→server surface without touching the hub; handlers
     * resolve sibling services via the {@link ServiceRegistry} at call time
     * (DIS: never constructor-inject across modules).
     */
    public void registerIntent(String type, Capability capability,
            BiConsumer<UUID, ProtocolMessage.Intent> action) {
        intentHandlers.put(type, new IntentHandler(capability, action));
    }

    /**
     * Server-authoritative intent handling: the client must have an open
     * session and hold the capability the handler requires (re-checked from the
     * session, which was permission-gated at handshake). Unknown or unauthorized
     * intents are dropped silently — a modified client gains nothing.
     */
    public void handleIntent(UUID playerId, ProtocolMessage.Intent intent) {
        ClientSession session = sessions.get(playerId);
        if (session == null) {
            log.debug("Intent {} dropped: {} has no session (must handshake first)", intent.type(), playerId);
            return;
        }
        IntentHandler handler = intentHandlers.get(intent.type());
        if (handler == null) {
            log.debug("Intent {} dropped: unknown to server", intent.type());
            return;
        }
        if (handler.capability() != null && !session.has(handler.capability())) {
            log.debug("Intent {} denied for {}: missing {}", intent.type(), playerId, handler.capability());
            return;
        }
        handler.action().accept(playerId, intent);
    }

    // --- Outbound projection ------------------------------------------------

    /** Sends a projection to one player iff they hold the capability. */
    public void project(UUID playerId, Capability capability, ProtocolMessage.Projection projection) {
        ClientSession session = sessions.get(playerId);
        if (session == null || !session.has(capability)) {
            return;
        }
        deliver(playerId, projection);
    }

    /** Sends an already-modelled message straight to a player's transport (no gating). */
    private void deliver(UUID playerId, ProtocolMessage message) {
        services.find(ProtocolTransport.class)
                .ifPresent(transport -> transport.send(playerId, ProtocolCodec.encode(message)));
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

        // Built-in inbound intents. More are contributed by the loader/web layer
        // via registerIntent (e.g. tpa.request, claim.abandon).
        registerIntent("session.ping", null, this::handlePing);
        registerIntent("claims.list", Capability.CLAIMS, this::handleClaimsList);

        log.info("Protocol hub active (projection layer for rock-client / rock-web)");
    }

    /** Wire-health keepalive: echoes the client's nonce straight back. */
    private void handlePing(UUID playerId, ProtocolMessage.Intent intent) {
        deliver(playerId, new ProtocolMessage.Projection("session.pong",
                Map.of("nonce", intent.fields().getOrDefault("nonce", ""))));
    }

    /**
     * On-demand claim list (CLAIMS capability): resolves ClaimService at call
     * time, streams one {@code claim.list.item} per owned claim, then a
     * {@code claim.list.end} with the count. Async off the tick thread.
     */
    private void handleClaimsList(UUID playerId, ProtocolMessage.Intent intent) {
        services.find(ClaimService.class).ifPresentOrElse(
                claims -> claims.findByOwner(new PlayerOwner(playerId)).thenAccept(owned -> {
                    for (RockClaim claim : owned) {
                        project(playerId, Capability.CLAIMS, new ProtocolMessage.Projection("claim.list.item", Map.of(
                                "id", claim.id().toString(),
                                "name", claim.displayName(),
                                "type", claim.type().name())));
                    }
                    project(playerId, Capability.CLAIMS, new ProtocolMessage.Projection("claim.list.end",
                            Map.of("count", String.valueOf(owned.size()))));
                }),
                () -> project(playerId, Capability.CLAIMS,
                        new ProtocolMessage.Projection("claim.list.end", Map.of("count", "0"))));
    }

    @Override
    public void onDisable() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
        sessions.clear();
    }
}
