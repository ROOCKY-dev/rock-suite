package dev.rock.protocol;

import com.google.inject.AbstractModule;
import com.google.inject.Scopes;

/**
 * Contributed platform module for rock-protocol (RFC-001). Loader adapters add
 * this alongside RockDataModule when they want client/web projection, then bind
 * a {@link ProtocolTransport} and route the custom-payload channel through the
 * {@link ProtocolHub}. Pure-vanilla servers simply omit it.
 */
public final class RockProtocolModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ProtocolHub.class).in(Scopes.SINGLETON);
    }
}
