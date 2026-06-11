package dev.rock.api.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/**
 * A rank or role (DMS). Lower priority number means higher priority.
 * Ties are resolved alphabetically by name ascending (DMS tie-breaking rule).
 */
public record RockGroup(UUID id, String name, int priority, Instant deletedAt) {

    /** DMS ordering: priority ascending, then name ascending for ties. */
    public static final Comparator<RockGroup> RESOLUTION_ORDER =
            Comparator.comparingInt(RockGroup::priority).thenComparing(RockGroup::name);

    public RockGroup {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
    }

    public boolean active() {
        return deletedAt == null;
    }
}
