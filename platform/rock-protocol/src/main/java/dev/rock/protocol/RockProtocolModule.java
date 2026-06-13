package dev.rock.protocol;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;
import dev.rock.api.protocol.ProtocolGateway;

/**
 * Contributed platform module for rock-protocol (RFC-001). Loader adapters add
 * this alongside RockDataModule when they want client/web projection. The
 * {@link ProtocolHub} is bound as the {@link ProtocolGateway} contract, so a
 * delivery channel (loader custom-payload, web WebSocket) resolves it via the
 * ServiceRegistry and registers its transport. Pure-vanilla servers omit it.
 */
public final class RockProtocolModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ProtocolHub.class).in(Scopes.SINGLETON);
        bind(ProtocolGateway.class).to(ProtocolHub.class);
    }
}
