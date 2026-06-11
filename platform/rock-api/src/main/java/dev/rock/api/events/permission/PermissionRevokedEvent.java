package dev.rock.api.events.permission;

import dev.rock.api.domain.owner.OwnerReference;
import dev.rock.api.event.Event;
import java.util.Objects;

/** Fired after a permission node is revoked from a subject. */
public record PermissionRevokedEvent(OwnerReference subject, String node) implements Event {

    public PermissionRevokedEvent {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(node, "node");
    }
}
