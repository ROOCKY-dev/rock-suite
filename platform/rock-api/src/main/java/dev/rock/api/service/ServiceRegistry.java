package dev.rock.api.service;

import java.util.Optional;
import java.util.Set;

/**
 * Platform-wide service discovery. Modules communicate exclusively through
 * services resolved here (or injected) and never through implementation
 * classes of other modules (AVD §8, REH §7).
 */
public interface ServiceRegistry extends RockService {

    /**
     * Registers a service implementation under its contract type.
     *
     * @throws IllegalStateException if a service is already registered for the contract
     */
    <T extends RockService> void register(Class<T> contract, T implementation);

    /** Replaces any existing registration for the contract. */
    <T extends RockService> void replace(Class<T> contract, T implementation);

    /** Resolves a service, throwing if absent. */
    <T extends RockService> T require(Class<T> contract);

    /** Resolves a service if present. */
    <T extends RockService> Optional<T> find(Class<T> contract);

    /** Removes a registration; returns true if one was removed. */
    <T extends RockService> boolean unregister(Class<T> contract);

    /** All currently registered contract types. */
    Set<Class<? extends RockService>> registeredContracts();
}
