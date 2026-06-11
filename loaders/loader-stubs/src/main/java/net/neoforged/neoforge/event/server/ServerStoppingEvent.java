package net.neoforged.neoforge.event.server;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.Event;

/** STUB — compile-time mirror. */
public abstract class ServerStoppingEvent extends Event {

    public abstract MinecraftServer getServer();
}
