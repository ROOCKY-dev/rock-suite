package dev.rock.api.services;

import dev.rock.api.domain.RockPlayer;
import dev.rock.api.service.RockService;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Canonical player identity service. Player data belongs to the platform (DMS). */
public interface PlayerService extends RockService {

    CompletableFuture<Optional<RockPlayer>> findById(UUID id);

    CompletableFuture<Optional<RockPlayer>> findByUsername(String username);

    /** Creates the player on first join or updates username/lastSeen. */
    CompletableFuture<RockPlayer> recordJoin(UUID id, String username);

    CompletableFuture<Void> recordLeave(UUID id);

    CompletableFuture<List<RockPlayer>> online();

    /** GDPR right-to-erasure (TRS §22): anonymises and emits PlayerDeletedEvent. */
    CompletableFuture<RockPlayer> erase(UUID id);
}
