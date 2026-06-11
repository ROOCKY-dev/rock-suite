package dev.rock.api.domain;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Canonical identity object for the entire platform (DMS). Player data belongs
 * to the platform; every module references the same RockPlayer.
 *
 * @param deletedAt null if active; supports right-to-erasure workflows
 */
public record RockPlayer(
        UUID id,
        String username,
        Locale preferredLocale,
        Instant firstJoin,
        Instant lastSeen,
        PlayerStatus status,
        Instant deletedAt) {

    /** Placeholder username applied after GDPR erasure (TRS §22). */
    public static final String ERASED_USERNAME = "deleted-user";

    public RockPlayer {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(preferredLocale, "preferredLocale");
        Objects.requireNonNull(firstJoin, "firstJoin");
        Objects.requireNonNull(lastSeen, "lastSeen");
        Objects.requireNonNull(status, "status");
    }

    public boolean active() {
        return deletedAt == null && status == PlayerStatus.ACTIVE;
    }

    /** Right-to-erasure: UUID retained for foreign keys, identity anonymised. */
    public RockPlayer erased(Instant when) {
        return new RockPlayer(id, ERASED_USERNAME, Locale.ROOT, firstJoin, lastSeen, PlayerStatus.DELETED, when);
    }
}
