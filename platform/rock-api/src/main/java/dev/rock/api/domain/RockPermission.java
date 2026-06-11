package dev.rock.api.domain;

import java.util.Objects;

/** A permission node and its state, e.g. {@code rock.claims.create} → ALLOW (DMS). */
public record RockPermission(String node, PermissionState state) {

    public RockPermission {
        Objects.requireNonNull(node, "node");
        Objects.requireNonNull(state, "state");
        if (node.isBlank()) {
            throw new IllegalArgumentException("permission node must not be blank");
        }
    }
}
