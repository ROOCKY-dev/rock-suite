package dev.rock.api.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A player team/party/guild (FTB Teams / Argonauts territory). Teams are
 * first-class owners: claims and economy accounts reference them via
 * {@code GroupOwner(team.id())} — one identity across all modules.
 *
 * @param deletedAt null if active (soft delete — DMS Rule 4)
 */
public record RockTeam(UUID id, String name, Instant created, Instant deletedAt) {

    public RockTeam {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(created, "created");
        if (name.isBlank()) {
            throw new IllegalArgumentException("team name must not be blank");
        }
    }

    public boolean active() {
        return deletedAt == null;
    }
}
