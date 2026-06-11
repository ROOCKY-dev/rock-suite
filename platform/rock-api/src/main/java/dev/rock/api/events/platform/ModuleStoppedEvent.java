package dev.rock.api.events.platform;

import dev.rock.api.event.Event;
import dev.rock.api.module.ModuleManifest;
import java.util.Objects;

/**
 * Fired when a module stops — normally or via crash isolation (TRS §17).
 *
 * @param failed true when the module was disabled because it crashed
 */
public record ModuleStoppedEvent(ModuleManifest manifest, boolean failed) implements Event {

    public ModuleStoppedEvent {
        Objects.requireNonNull(manifest, "manifest");
    }
}
