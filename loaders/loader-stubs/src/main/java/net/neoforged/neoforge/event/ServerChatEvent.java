package net.neoforged.neoforge.event;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** STUB — compile-time mirror of the NeoForge server chat event. */
public abstract class ServerChatEvent extends Event implements ICancellableEvent {

    public abstract ServerPlayer getPlayer();

    public abstract String getRawText();
}
