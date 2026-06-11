package dev.rock.api.events.platform;

import dev.rock.api.event.Event;
import dev.rock.api.module.ModuleManifest;
import java.util.Objects;

/** Fired after a module is loaded (before enable). */
public record ModuleLoadedEvent(ModuleManifest manifest) implements Event {

    public ModuleLoadedEvent {
        Objects.requireNonNull(manifest, "manifest");
    }
}
