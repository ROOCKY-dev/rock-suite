package dev.rock.core.bootstrap;

import dev.rock.api.domain.RockServer;
import java.nio.file.Path;
import java.util.concurrent.Executor;

/**
 * Everything the platform needs from its host. Implemented by each loader
 * adapter; the platform itself never touches loader APIs (AVD §5).
 */
public interface PlatformEnvironment {

    /** Executes tasks on the main server (tick) thread. */
    Executor mainThreadExecutor();

    /** Root directory for ROCK config and data files. */
    Path dataDirectory();

    RockServer serverInfo();
}
