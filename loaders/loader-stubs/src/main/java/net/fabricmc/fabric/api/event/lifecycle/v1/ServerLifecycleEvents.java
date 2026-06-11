package net.fabricmc.fabric.api.event.lifecycle.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.server.MinecraftServer;

/** STUB — compile-time mirror of Fabric API server lifecycle events. */
public final class ServerLifecycleEvents {

    public static final Event<ServerStarted> SERVER_STARTED = null;
    public static final Event<ServerStopping> SERVER_STOPPING = null;

    private ServerLifecycleEvents() {
    }

    @FunctionalInterface
    public interface ServerStarted {
        void onServerStarted(MinecraftServer server);
    }

    @FunctionalInterface
    public interface ServerStopping {
        void onServerStopping(MinecraftServer server);
    }
}
