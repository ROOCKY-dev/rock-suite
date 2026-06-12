package dev.rock.protocol;

import dev.rock.api.annotations.RockInternal;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArraySet;

/** Per-player protocol state: negotiated version + the capabilities they hold. */
@RockInternal
public final class ClientSession {

    private final UUID playerId;
    private final int protocolVersion;
    private final Set<Capability> capabilities = new CopyOnWriteArraySet<>();

    ClientSession(UUID playerId, int protocolVersion) {
        this.playerId = playerId;
        this.protocolVersion = protocolVersion;
    }

    public UUID playerId() {
        return playerId;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public boolean has(Capability capability) {
        return capabilities.contains(capability);
    }

    void grant(Capability capability) {
        capabilities.add(capability);
    }

    public Set<Capability> capabilities() {
        return Set.copyOf(capabilities);
    }
}
