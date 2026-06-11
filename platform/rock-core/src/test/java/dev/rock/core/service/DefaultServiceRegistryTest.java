package dev.rock.core.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rock.api.service.RockService;
import org.junit.jupiter.api.Test;

class DefaultServiceRegistryTest {

    interface GreetingService extends RockService {
        String greet();
    }

    private final DefaultServiceRegistry registry = new DefaultServiceRegistry();
    private final GreetingService impl = () -> "hello";

    @Test
    void registerAndResolve() {
        registry.register(GreetingService.class, impl);

        assertEquals("hello", registry.require(GreetingService.class).greet());
        assertTrue(registry.find(GreetingService.class).isPresent());
        assertTrue(registry.registeredContracts().contains(GreetingService.class));
    }

    @Test
    void duplicateRegistrationOfDifferentInstanceFails() {
        registry.register(GreetingService.class, impl);

        assertThrows(IllegalStateException.class,
                () -> registry.register(GreetingService.class, () -> "other"));
    }

    @Test
    void requireMissingServiceFails() {
        assertThrows(IllegalStateException.class, () -> registry.require(GreetingService.class));
    }

    @Test
    void unregisterRemoves() {
        registry.register(GreetingService.class, impl);

        assertTrue(registry.unregister(GreetingService.class));
        assertTrue(registry.find(GreetingService.class).isEmpty());
    }
}
