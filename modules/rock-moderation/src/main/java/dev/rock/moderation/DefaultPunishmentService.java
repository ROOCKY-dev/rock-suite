package dev.rock.moderation;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.data.DataService;
import dev.rock.api.data.RowMapper;
import dev.rock.api.domain.AuditAction;
import dev.rock.api.domain.PunishmentType;
import dev.rock.api.domain.RockPunishment;
import dev.rock.api.domain.owner.OwnerReference;
import dev.rock.api.event.EventBus;
import dev.rock.api.events.punishment.PlayerBanEvent;
import dev.rock.api.events.punishment.PunishmentAppliedEvent;
import dev.rock.api.events.punishment.PunishmentRevokedEvent;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.api.services.AuditService;
import dev.rock.api.services.PunishmentService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Moderation engine over the unified RockPunishment domain (DMS). Active
 * punishments live in a tick-thread-safe cache consulted by the join gate
 * (bans) and the chat listener (mutes); every mutation is audited (TRS §9).
 */
@RockInternal
@Singleton
public final class DefaultPunishmentService implements PunishmentService, LifecycleAware {

    private static final RowMapper<RockPunishment> PUNISHMENT_MAPPER = row -> new RockPunishment(
            row.getUuid("id"),
            PunishmentType.valueOf(row.getString("punishment_type")),
            row.getUuid("target"),
            OwnerReference.deserialize(row.getString("issuer_ref")),
            row.getString("reason"),
            row.getInstant("created"),
            row.getInstant("expires"),
            row.getInstant("revoked_at"),
            row.getUuid("revoked_by"));

    /** Raised when a ban is vetoed by an event listener. */
    public static final class PunishmentVetoedException extends RuntimeException {
        public PunishmentVetoedException(String message) {
            super(message);
        }
    }

    private final DataService data;
    private final EventBus eventBus;
    private final ServiceRegistry services;

    // target → (type → active punishment); maintained on every mutation.
    private final Map<UUID, Map<PunishmentType, RockPunishment>> activeByTarget = new ConcurrentHashMap<>();

    @Inject
    public DefaultPunishmentService(DataService data, EventBus eventBus, ServiceRegistry services) {
        this.data = data;
        this.eventBus = eventBus;
        this.services = services;
    }

    @Override
    public void onEnable() {
        long now = Instant.now().toEpochMilli();
        List<RockPunishment> active = data.query("""
                SELECT * FROM rock_punishments
                WHERE revoked_at IS NULL AND (expires IS NULL OR expires > :now)
                """, Map.of("now", now), PUNISHMENT_MAPPER).join();
        active.forEach(this::cache);
    }

    @Override
    public void onDisable() {
        activeByTarget.clear();
    }

    private void cache(RockPunishment punishment) {
        activeByTarget.computeIfAbsent(punishment.target(), k -> new ConcurrentHashMap<>())
                .put(punishment.type(), punishment);
    }

    private void uncache(RockPunishment punishment) {
        Map<PunishmentType, RockPunishment> byType = activeByTarget.get(punishment.target());
        if (byType != null) {
            byType.remove(punishment.type());
        }
    }

    @Override
    public CompletableFuture<RockPunishment> punish(
            PunishmentType type, UUID target, OwnerReference issuer, String reason, Duration duration) {
        Instant now = Instant.now();
        Instant expires = duration == null ? null : now.plus(duration);
        RockPunishment punishment = new RockPunishment(
                UUID.randomUUID(), type, target, issuer, reason, now, expires, null, null);

        if (type == PunishmentType.BAN) {
            PlayerBanEvent preEvent = eventBus.publish(new PlayerBanEvent(punishment));
            if (preEvent.cancelled()) {
                return CompletableFuture.failedFuture(
                        new PunishmentVetoedException("Ban cancelled by event listener"));
            }
        }

        Map<String, Object> params = new HashMap<>();
        params.put("id", punishment.id().toString());
        params.put("type", type.name());
        params.put("target", target.toString());
        params.put("issuer", issuer.serialize());
        params.put("reason", reason);
        params.put("created", now.toEpochMilli());
        params.put("expires", expires == null ? null : expires.toEpochMilli());

        return data.update("""
                INSERT INTO rock_punishments
                    (id, punishment_type, target, issuer_ref, reason, created, expires, revoked_at, revoked_by)
                VALUES (:id, :type, :target, :issuer, :reason, :created, :expires, NULL, NULL)
                """, params)
                .thenApply(rows -> {
                    cache(punishment);
                    eventBus.publish(new PunishmentAppliedEvent(punishment));
                    audit(issuer, type == PunishmentType.BAN ? AuditAction.BAN : AuditAction.CREATE,
                            target, "{\"type\":\"" + type + "\",\"reason\":\"" + reason + "\"}");
                    return punishment;
                });
    }

    @Override
    public CompletableFuture<Void> revoke(UUID punishmentId, UUID revokedBy) {
        long now = Instant.now().toEpochMilli();
        return data.queryOne("SELECT * FROM rock_punishments WHERE id = :id",
                        Map.of("id", punishmentId.toString()), PUNISHMENT_MAPPER)
                .thenCompose(found -> {
                    RockPunishment punishment = found.orElseThrow(
                            () -> new IllegalArgumentException("No punishment " + punishmentId));
                    return data.update("""
                            UPDATE rock_punishments SET revoked_at = :now, revoked_by = :by
                            WHERE id = :id AND revoked_at IS NULL
                            """,
                            Map.of("now", now, "by", revokedBy.toString(), "id", punishmentId.toString()))
                            .thenAccept(rows -> {
                                if (rows > 0) {
                                    uncache(punishment);
                                    eventBus.publish(new PunishmentRevokedEvent(punishment));
                                    audit(punishment.issuer(),
                                            punishment.type() == PunishmentType.BAN
                                                    ? AuditAction.UNBAN : AuditAction.REVOKE,
                                            punishment.target(),
                                            "{\"punishment\":\"" + punishmentId + "\"}");
                                }
                            });
                });
    }

    @Override
    public Optional<RockPunishment> activeCached(UUID target, PunishmentType type) {
        Map<PunishmentType, RockPunishment> byType = activeByTarget.get(target);
        if (byType == null) {
            return Optional.empty();
        }
        RockPunishment punishment = byType.get(type);
        if (punishment == null) {
            return Optional.empty();
        }
        // Lazy expiry: drop on first read past the deadline.
        if (!punishment.activeAt(Instant.now())) {
            byType.remove(type);
            return Optional.empty();
        }
        return Optional.of(punishment);
    }

    @Override
    public CompletableFuture<List<RockPunishment>> history(UUID target) {
        return data.query("SELECT * FROM rock_punishments WHERE target = :t ORDER BY created DESC",
                Map.of("t", target.toString()), PUNISHMENT_MAPPER);
    }

    private void audit(OwnerReference actor, AuditAction action, UUID target, String details) {
        services.find(AuditService.class).ifPresent(audit ->
                audit.record(actor, action, "PLAYER", target, details));
    }
}
