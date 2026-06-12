package dev.rock.core.loader;

import dev.rock.api.domain.PlayerStatus;
import dev.rock.api.domain.PunishmentType;
import dev.rock.api.domain.RockPlayer;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.player.PlayerChatEvent;
import dev.rock.api.events.player.PlayerJoinEvent;
import dev.rock.api.events.player.PlayerLeaveEvent;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.api.services.PlayerService;
import dev.rock.api.services.PunishmentService;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Loader-agnostic player session handling. Loader adapters feed raw
 * (uuid, username) pairs from their connection callbacks; this bridge persists
 * via {@link PlayerService} (when rock-data is installed) and publishes the
 * platform events. Persistence and event publication run asynchronously —
 * never on the network/tick thread (TRS §3).
 */
public final class PlayerSessionBridge {

    private static final Logger log = LoggerFactory.getLogger(PlayerSessionBridge.class);

    private final ServiceRegistry services;
    private final EventBus eventBus;

    public PlayerSessionBridge(ServiceRegistry services, EventBus eventBus) {
        this.services = Objects.requireNonNull(services);
        this.eventBus = Objects.requireNonNull(eventBus);
    }

    private Optional<PlayerService> playerService() {
        return services.find(PlayerService.class);
    }

    /**
     * Ban gate, called by loader adapters BEFORE the connection completes
     * (tick-safe — consults the moderation cache only). Returns the
     * disconnect message when the player is banned; empty means allowed.
     */
    public Optional<String> joinDenialReason(UUID id) {
        return services.find(PunishmentService.class)
                .flatMap(punishments -> punishments.activeCached(id, PunishmentType.BAN))
                .map(ban -> {
                    String until = ban.expires() == null ? "permanently" : "until " + ban.expires();
                    return "You are banned " + until + ": " + ban.reason();
                });
    }

    /** Called by loader adapters when a player connection is ready. */
    public void playerJoined(UUID id, String username) {
        Optional<PlayerService> service = playerService();
        if (service.isPresent()) {
            service.get().recordJoin(id, username)
                    .thenAccept(player -> {
                        // Insert sets firstJoin == lastSeen; equality identifies a first join.
                        boolean firstJoin = player.firstJoin().equals(player.lastSeen());
                        eventBus.publish(new PlayerJoinEvent(player, firstJoin));
                    })
                    .exceptionally(e -> {
                        log.error("Failed to record join for {}", username, e);
                        return null;
                    });
        } else {
            eventBus.publishAsync(new PlayerJoinEvent(transient_(id, username), true));
        }
    }

    /** Called by loader adapters when a player disconnects. */
    public void playerLeft(UUID id, String username) {
        Optional<PlayerService> service = playerService();
        if (service.isPresent()) {
            RockPlayer snapshot = transient_(id, username);
            service.get().recordLeave(id)
                    .thenAccept(v -> eventBus.publish(new PlayerLeaveEvent(snapshot)))
                    .exceptionally(e -> {
                        log.error("Failed to record leave for {}", username, e);
                        return null;
                    });
        } else {
            eventBus.publishAsync(new PlayerLeaveEvent(transient_(id, username)));
        }
    }

    /**
     * Called by loader adapters before a chat message is broadcast (sync,
     * in-memory listeners only).
     *
     * @return true when the message may be broadcast; false when cancelled
     *         (mute, filter)
     */
    public boolean playerChatted(UUID id, String username, String message) {
        PlayerChatEvent event = eventBus.publish(new PlayerChatEvent(id, username, message));
        return !event.cancelled();
    }

    private static RockPlayer transient_(UUID id, String username) {
        Instant now = Instant.now();
        return new RockPlayer(id, username, Locale.ROOT, now, now, PlayerStatus.ACTIVE, null);
    }
}
