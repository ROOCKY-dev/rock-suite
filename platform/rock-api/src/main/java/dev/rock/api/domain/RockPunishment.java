package dev.rock.api.domain;

import dev.rock.api.domain.owner.OwnerReference;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * One unified record for all punishment types (DMS).
 *
 * @param expires   null = permanent
 * @param revokedAt null if active; non-null if manually revoked
 * @param revokedBy nullable — who revoked it
 */
public record RockPunishment(
        UUID id,
        PunishmentType type,
        UUID target,
        OwnerReference issuer,
        String reason,
        Instant created,
        Instant expires,
        Instant revokedAt,
        UUID revokedBy) {

    public RockPunishment {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(issuer, "issuer");
        Objects.requireNonNull(reason, "reason");
        Objects.requireNonNull(created, "created");
    }

    public boolean activeAt(Instant when) {
        return revokedAt == null && (expires == null || expires.isAfter(when));
    }
}
