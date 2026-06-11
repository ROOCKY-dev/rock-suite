package dev.rock.api.events.platform;

import dev.rock.api.event.Event;
import dev.rock.api.module.ModuleManifest;
import java.util.Objects;

/** Fired once a module reaches RUNNING. */
public record ModuleStartedEvent(ModuleManifest manifest) implements Event {

    public ModuleStartedEvent {
        Objects.requireNonNull(manifest, "manifest");
    }
}
