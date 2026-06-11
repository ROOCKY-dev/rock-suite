package dev.rock.api.events.permission;

import dev.rock.api.domain.owner.OwnerReference;
import dev.rock.api.event.Event;
import java.util.Objects;

/** Fired after a permission node is granted to a subject (player or group). */
public record PermissionGrantedEvent(OwnerReference subject, String node) implements Event {

    public PermissionGrantedEvent {
        Objects.requireNonNull(subject, "subject");
        Objects.requireNonNull(node, "node");
    }
}
