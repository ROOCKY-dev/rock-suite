package me.lucko.fabric.api.permissions.v0;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.commands.SharedSuggestionProvider;

/**
 * STUB — compile-time mirror of lucko/fabric-permissions-api v0: the de-facto
 * permission hook every serious Fabric mod checks against.
 */
@FunctionalInterface
public interface PermissionCheckEvent {

    Event<PermissionCheckEvent> EVENT = null;

    TriState onPermissionCheck(SharedSuggestionProvider source, String permission);
}
