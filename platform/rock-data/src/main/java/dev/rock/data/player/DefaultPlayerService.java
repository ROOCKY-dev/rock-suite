package dev.rock.data.player;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.data.DataService;
import dev.rock.api.data.RowMapper;
import dev.rock.api.domain.PlayerStatus;
import dev.rock.api.domain.RockPlayer;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.player.PlayerDeletedEvent;
import dev.rock.api.services.PlayerService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Platform-owned player identity (DMS: player data belongs to the platform).
 * Backed entirely by the async DataService — no JDBC on the game thread.
 */
@RockInternal
@Singleton
public final class DefaultPlayerService implements PlayerService {

    private static final RowMapper<RockPlayer> PLAYER_MAPPER = row -> new RockPlayer(
            row.getUuid("id"),
            row.getString("username"),
            Locale.forLanguageTag(row.getString("preferred_locale")),
            row.getInstant("first_join"),
            row.getInstant("last_seen"),
            PlayerStatus.valueOf(row.getString("status")),
            row.getInstant("deleted_at"));

    private final DataService data;
    private final EventBus eventBus;
    private final Set<UUID> online = ConcurrentHashMap.newKeySet();

    @Inject
    public DefaultPlayerService(DataService data, EventBus eventBus) {
        this.data = data;
        this.eventBus = eventBus;
    }

    @Override
    public CompletableFuture<Optional<RockPlayer>> findById(UUID id) {
        return data.queryOne("SELECT * FROM rock_players WHERE id = :id",
                Map.of("id", id.toString()), PLAYER_MAPPER);
    }

    @Override
    public CompletableFuture<Optional<RockPlayer>> findByUsername(String username) {
        return data.queryOne("SELECT * FROM rock_players WHERE username = :username AND deleted_at IS NULL",
                Map.of("username", username), PLAYER_MAPPER);
    }

    @Override
    public CompletableFuture<RockPlayer> recordJoin(UUID id, String username) {
        long now = Instant.now().toEpochMilli();
        return data.inTransaction(tx -> {
            Optional<RockPlayer> existing = tx.queryOne("SELECT * FROM rock_players WHERE id = :id",
                    Map.of("id", id.toString()), PLAYER_MAPPER);
            if (existing.isEmpty()) {
                // first_join == last_seen marks a first join for the session bridge
                tx.update("""
                        INSERT INTO rock_players (id, username, preferred_locale, first_join, last_seen, status, deleted_at)
                        VALUES (:id, :username, :locale, :now, :now, :status, NULL)
                        """,
                        Map.of("id", id.toString(), "username", username, "locale", "und",
                                "now", now, "status", PlayerStatus.ACTIVE.name()));
            } else {
                tx.update("UPDATE rock_players SET username = :username, last_seen = :now WHERE id = :id",
                        Map.of("id", id.toString(), "username", username, "now", now));
            }
            return tx.queryOne("SELECT * FROM rock_players WHERE id = :id",
                    Map.of("id", id.toString()), PLAYER_MAPPER).orElseThrow();
        }).thenApply(player -> {
            online.add(id);
            return player;
        });
    }

    @Override
    public CompletableFuture<Void> recordLeave(UUID id) {
        online.remove(id);
        return data.update("UPDATE rock_players SET last_seen = :now WHERE id = :id",
                Map.of("id", id.toString(), "now", Instant.now().toEpochMilli()))
                .thenApply(rows -> null);
    }

    @Override
    public CompletableFuture<List<RockPlayer>> online() {
        if (online.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        // UUID set is bounded by player count; an IN clause is fine at this scale.
        String ids = String.join("','", online.stream().map(UUID::toString).toList());
        return data.query("SELECT * FROM rock_players WHERE id IN ('" + ids + "')", Map.of(), PLAYER_MAPPER);
    }

    @Override
    public CompletableFuture<RockPlayer> erase(UUID id) {
        long now = Instant.now().toEpochMilli();
        return data.inTransaction(tx -> {
            tx.update("""
                    UPDATE rock_players
                    SET username = :erased, preferred_locale = 'und', status = :status, deleted_at = :now
                    WHERE id = :id
                    """,
                    Map.of("erased", RockPlayer.ERASED_USERNAME, "status", PlayerStatus.DELETED.name(),
                            "now", now, "id", id.toString()));
            return tx.queryOne("SELECT * FROM rock_players WHERE id = :id",
                    Map.of("id", id.toString()), PLAYER_MAPPER).orElseThrow();
        }).thenApply(player -> {
            // Modules clean up their own data in response (TRS §22).
            eventBus.publish(new PlayerDeletedEvent(id));
            return player;
        });
    }
}
