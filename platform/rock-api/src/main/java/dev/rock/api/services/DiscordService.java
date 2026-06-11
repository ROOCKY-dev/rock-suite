package dev.rock.api.services;

import dev.rock.api.domain.RockDiscordLink;
import dev.rock.api.service.RockService;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Discord integration contract. All sends are queued and rate-limited; messages
 * are never sent from the Minecraft game thread (TRS §13).
 */
public interface DiscordService extends RockService {

    /** Enqueues a message for the given channel; delivery is asynchronous. */
    CompletableFuture<Void> sendMessage(String channelId, String content);

    CompletableFuture<RockDiscordLink> link(UUID playerId, String discordId);

    CompletableFuture<Void> unlink(UUID playerId);

    CompletableFuture<Optional<RockDiscordLink>> linkOf(UUID playerId);
}
