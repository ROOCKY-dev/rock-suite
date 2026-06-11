package dev.rock.discord;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.data.DataService;
import dev.rock.api.data.RowMapper;
import dev.rock.api.domain.RockDiscordLink;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.services.DiscordService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Discord integration: queued, rate-limited message delivery plus
 * player↔Discord identity links persisted via DataService.
 */
@RockInternal
@Singleton
public final class DefaultDiscordService implements DiscordService, LifecycleAware {

    private static final RowMapper<RockDiscordLink> LINK_MAPPER = row -> new RockDiscordLink(
            row.getUuid("player_id"),
            row.getString("discord_id"),
            row.getInstant("linked_at"),
            row.getInstant("unlinked_at"));

    private final DataService data;
    private final DiscordMessageQueue queue;

    @Inject
    public DefaultDiscordService(DataService data, DiscordMessageQueue queue) {
        this.data = data;
        this.queue = queue;
    }

    @Override
    public CompletableFuture<Void> sendMessage(String channelId, String content) {
        return queue.enqueue(channelId, content);
    }

    @Override
    public CompletableFuture<RockDiscordLink> link(UUID playerId, String discordId) {
        Instant now = Instant.now();
        RockDiscordLink link = new RockDiscordLink(playerId, discordId, now, null);
        return data.inTransaction(tx -> {
            tx.update("DELETE FROM rock_discord_links WHERE player_id = :p",
                    Map.of("p", playerId.toString()));
            tx.update("""
                    INSERT INTO rock_discord_links (player_id, discord_id, linked_at, unlinked_at)
                    VALUES (:p, :d, :t, NULL)
                    """,
                    Map.of("p", playerId.toString(), "d", discordId, "t", now.toEpochMilli()));
            return link;
        });
    }

    @Override
    public CompletableFuture<Void> unlink(UUID playerId) {
        return data.update("UPDATE rock_discord_links SET unlinked_at = :t WHERE player_id = :p AND unlinked_at IS NULL",
                Map.of("t", Instant.now().toEpochMilli(), "p", playerId.toString()))
                .thenApply(rows -> null);
    }

    @Override
    public CompletableFuture<Optional<RockDiscordLink>> linkOf(UUID playerId) {
        return data.queryOne("SELECT * FROM rock_discord_links WHERE player_id = :p",
                Map.of("p", playerId.toString()), LINK_MAPPER);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        queue.close();
    }
}
