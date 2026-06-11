package dev.rock.api.domain;

import dev.rock.api.domain.owner.OwnerReference;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Immutable history record (DMS). Audit entries are never updated and never
 * deleted except by explicit administrative data purge.
 *
 * @param details JSON-serialised action context
 */
public record RockAuditEntry(
        UUID id,
        Instant timestamp,
        OwnerReference actor,
        AuditAction action,
        String targetType,
        UUID targetId,
        String details) {

    public RockAuditEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(timestamp, "timestamp");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(targetType, "targetType");
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(details, "details");
    }
}
