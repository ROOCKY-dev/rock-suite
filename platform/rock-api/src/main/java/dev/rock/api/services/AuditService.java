package dev.rock.api.services;

import dev.rock.api.domain.AuditAction;
import dev.rock.api.domain.RockAuditEntry;
import dev.rock.api.domain.owner.OwnerReference;
import dev.rock.api.service.RockService;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Append-only audit log: administrative actions MUST produce entries (TRS §9). */
public interface AuditService extends RockService {

    CompletableFuture<RockAuditEntry> record(
            OwnerReference actor, AuditAction action, String targetType, UUID targetId, String detailsJson);

    CompletableFuture<List<RockAuditEntry>> findByTarget(String targetType, UUID targetId, int limit);

    CompletableFuture<List<RockAuditEntry>> recent(int limit);
}
