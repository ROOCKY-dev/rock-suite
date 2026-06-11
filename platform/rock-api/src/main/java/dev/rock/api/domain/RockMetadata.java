package dev.rock.api.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Extensible key-value storage attached to any domain entity (DMS). Lets
 * modules store custom data without schema migrations.
 *
 * @param entityType "PLAYER", "CLAIM", "GROUP", ...
 * @param namespace  owning module, e.g. "rock.discord"
 * @param value      JSON-serialised value
 */
public record RockMetadata(
        UUID entityId,
        String entityType,
        String namespace,
        String key,
        String value,
        Instant lastModified) {

    public RockMetadata {
        Objects.requireNonNull(entityId, "entityId");
        Objects.requireNonNull(entityType, "entityType");
        Objects.requireNonNull(namespace, "namespace");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(lastModified, "lastModified");
    }
}
