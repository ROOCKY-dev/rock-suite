package dev.rock.api.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Connects Minecraft and Discord identities (DMS). No Discord logic belongs
 * inside RockPlayer.
 *
 * @param unlinkedAt null if currently linked
 */
public record RockDiscordLink(UUID playerId, String discordId, Instant linkedAt, Instant unlinkedAt) {

    public RockDiscordLink {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(discordId, "discordId");
        Objects.requireNonNull(linkedAt, "linkedAt");
    }

    public boolean linked() {
        return unlinkedAt == null;
    }
}
