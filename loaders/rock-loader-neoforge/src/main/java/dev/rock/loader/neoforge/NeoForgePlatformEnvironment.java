package dev.rock.loader.neoforge;

import dev.rock.api.domain.RockServer;
import dev.rock.api.domain.ServerType;
import dev.rock.core.bootstrap.PlatformEnvironment;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.Executor;
import net.minecraft.server.MinecraftServer;

/** NeoForge host environment: the server itself is the tick-thread executor. */
final class NeoForgePlatformEnvironment implements PlatformEnvironment {

    private final MinecraftServer server;
    private final RockServer info;

    NeoForgePlatformEnvironment(MinecraftServer server) {
        this.server = server;
        this.info = new RockServer(
                UUID.randomUUID(), "neoforge-server", server.getServerVersion(), ServerType.NEOFORGE);
    }

    @Override
    public Executor mainThreadExecutor() {
        return server;
    }

    @Override
    public Path dataDirectory() {
        return server.getServerDirectory().resolve("rock");
    }

    @Override
    public RockServer serverInfo() {
        return info;
    }
}
