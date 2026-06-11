package dev.rock.core.service;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.service.RockService;
import dev.rock.api.service.ServiceRegistry;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe service registry (RPS §5). */
@RockInternal
public final class DefaultServiceRegistry implements ServiceRegistry, RockService {

    private final Map<Class<? extends RockService>, RockService> services = new ConcurrentHashMap<>();

    @Override
    public <T extends RockService> void register(Class<T> contract, T implementation) {
        RockService existing = services.putIfAbsent(contract, implementation);
        if (existing != null && existing != implementation) {
            throw new IllegalStateException(
                    "Service already registered for " + contract.getName() + ": " + existing.serviceName());
        }
    }

    @Override
    public <T extends RockService> void replace(Class<T> contract, T implementation) {
        services.put(contract, implementation);
    }

    @Override
    public <T extends RockService> T require(Class<T> contract) {
        return find(contract).orElseThrow(
                () -> new IllegalStateException("No service registered for " + contract.getName()));
    }

    @Override
    public <T extends RockService> Optional<T> find(Class<T> contract) {
        return Optional.ofNullable(contract.cast(services.get(contract)));
    }

    @Override
    public <T extends RockService> boolean unregister(Class<T> contract) {
        return services.remove(contract) != null;
    }

    @Override
    public Set<Class<? extends RockService>> registeredContracts() {
        return Set.copyOf(services.keySet());
    }
}
