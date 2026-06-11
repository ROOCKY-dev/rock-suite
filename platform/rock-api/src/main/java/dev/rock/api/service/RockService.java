package dev.rock.api.service;

/**
 * Marker interface for all ROCK platform services. Any Guice-instantiated type
 * implementing this interface is automatically registered with the
 * {@link ServiceRegistry} by the platform (DIS §8).
 */
public interface RockService {

    /** Human-readable service name, used for diagnostics and the service registry. */
    default String serviceName() {
        return getClass().getSimpleName();
    }
}
