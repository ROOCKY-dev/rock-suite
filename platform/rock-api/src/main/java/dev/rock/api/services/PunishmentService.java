package dev.rock.api.services;

import dev.rock.api.domain.PunishmentType;
import dev.rock.api.domain.RockPunishment;
import dev.rock.api.domain.owner.OwnerReference;
import dev.rock.api.service.RockService;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Unified moderation contract over the RockPunishment domain (DMS). Bans gate
 * joins, mutes gate chat — both checked through the tick-thread-safe cache.
 * Every mutation produces a RockAuditEntry (TRS §9).
 */
public interface PunishmentService extends RockService {

    /**
     * Applies a punishment. BAN publishes the cancellable PlayerBanEvent first;
     * a cancelled ban fails the future with PunishmentVetoedException semantics.
     *
     * @param duration null = permanent
     */
    CompletableFuture<RockPunishment> punish(
            PunishmentType type, UUID target, OwnerReference issuer, String reason, Duration duration);

    /** Revokes an active punishment (unban/unmute). */
    CompletableFuture<Void> revoke(UUID punishmentId, UUID revokedBy);

    /** Active punishment of a type for a player; cache-backed, tick-thread safe. */
    Optional<RockPunishment> activeCached(UUID target, PunishmentType type);

    CompletableFuture<List<RockPunishment>> history(UUID target);
}
