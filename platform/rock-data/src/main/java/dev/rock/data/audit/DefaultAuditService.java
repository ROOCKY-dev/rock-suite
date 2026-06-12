package dev.rock.data.audit;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.data.DataService;
import dev.rock.api.data.RowMapper;
import dev.rock.api.domain.AuditAction;
import dev.rock.api.domain.RockAuditEntry;
import dev.rock.api.domain.owner.OwnerReference;
import dev.rock.api.services.AuditService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Platform-owned append-only audit log (TRS §9). Entries are immutable —
 * inserts only; deletion is reserved for administrative purge (DMS).
 */
@RockInternal
@Singleton
public final class DefaultAuditService implements AuditService {

    private static final RowMapper<RockAuditEntry> ENTRY_MAPPER = row -> new RockAuditEntry(
            row.getUuid("id"),
            row.getInstant("ts"),
            OwnerReference.deserialize(row.getString("actor_ref")),
            AuditAction.valueOf(row.getString("action")),
            row.getString("target_type"),
            row.getUuid("target_id"),
            row.getString("details"));

    private final DataService data;

    @Inject
    public DefaultAuditService(DataService data) {
        this.data = data;
    }

    @Override
    public CompletableFuture<RockAuditEntry> record(
            OwnerReference actor, AuditAction action, String targetType, UUID targetId, String detailsJson) {
        RockAuditEntry entry = new RockAuditEntry(
                UUID.randomUUID(), Instant.now(), actor, action, targetType, targetId, detailsJson);
        return data.update("""
                INSERT INTO rock_audit (id, ts, actor_ref, action, target_type, target_id, details)
                VALUES (:id, :ts, :actor, :action, :type, :target, :details)
                """,
                Map.of("id", entry.id().toString(), "ts", entry.timestamp().toEpochMilli(),
                        "actor", actor.serialize(), "action", action.name(),
                        "type", targetType, "target", targetId.toString(), "details", detailsJson))
                .thenApply(rows -> entry);
    }

    @Override
    public CompletableFuture<List<RockAuditEntry>> findByTarget(String targetType, UUID targetId, int limit) {
        return data.query("""
                SELECT * FROM rock_audit WHERE target_type = :type AND target_id = :target
                ORDER BY ts DESC LIMIT :limit
                """,
                Map.of("type", targetType, "target", targetId.toString(), "limit", limit), ENTRY_MAPPER);
    }

    @Override
    public CompletableFuture<List<RockAuditEntry>> recent(int limit) {
        return data.query("SELECT * FROM rock_audit ORDER BY ts DESC LIMIT :limit",
                Map.of("limit", limit), ENTRY_MAPPER);
    }
}
