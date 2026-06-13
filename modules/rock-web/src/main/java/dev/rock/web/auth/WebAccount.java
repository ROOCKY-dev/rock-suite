package dev.rock.web.auth;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A web dashboard login account (TRS §12). */
public record WebAccount(
        UUID id,
        String username,
        String passwordHash,
        WebRole role,
        UUID playerId,
        Instant created,
        Instant lastLogin) {

    public WebAccount {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(passwordHash, "passwordHash");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(created, "created");
    }

    /** Dashboard authorization roles. */
    public enum WebRole {
        /** Full access incl. admin endpoints. */
        ADMIN,
        /** Read-only / self-scoped access. */
        USER
    }
}
