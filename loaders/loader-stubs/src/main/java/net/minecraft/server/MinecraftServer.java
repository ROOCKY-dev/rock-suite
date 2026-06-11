package net.minecraft.server;

import java.nio.file.Path;
import java.util.concurrent.Executor;

/**
 * STUB — compile-time mirror of the dedicated server. The real class is a
 * blockable event loop; {@link Executor#execute} schedules onto the tick thread.
 */
public abstract class MinecraftServer implements Executor {

    public abstract String getServerVersion();

    /** Mirrors MinecraftServer#getServerDirectory (the run directory). */
    public abstract Path getServerDirectory();
}
