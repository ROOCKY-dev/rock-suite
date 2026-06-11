package net.neoforged.neoforge.event.entity.player;

import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;

/** STUB — compile-time mirror of NeoForge player connection events. */
public abstract class PlayerEvent extends Event {

    public abstract Player getEntity();

    public abstract static class PlayerLoggedInEvent extends PlayerEvent {
    }

    public abstract static class PlayerLoggedOutEvent extends PlayerEvent {
    }
}
