package dev.rock.core.guice;

import com.google.inject.TypeLiteral;
import com.google.inject.matcher.Matcher;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import dev.rock.api.annotations.RockInternal;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.service.RockService;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.core.lifecycle.LifecycleManager;
import java.util.Objects;

/**
 * Correct implementation of DIS §8 (see Architectural Review D-2): a single
 * platform-level TypeListener pair that (a) auto-registers every instantiated
 * {@link RockService} under each service contract it implements, and
 * (b) feeds {@link LifecycleAware} instances to the {@link LifecycleManager}.
 *
 * <p>Type listeners are inherited by child injectors, so module services are
 * discovered without any registration code in the modules (DIS §8 intent).
 */
@RockInternal
public final class PlatformListeners {

    private PlatformListeners() {
    }

    /** Matches types assignable to the given supertype. */
    public static final class SubtypeMatcher implements Matcher<TypeLiteral<?>> {
        private final Class<?> supertype;

        public SubtypeMatcher(Class<?> supertype) {
            this.supertype = Objects.requireNonNull(supertype);
        }

        @Override
        public boolean matches(TypeLiteral<?> type) {
            return supertype.isAssignableFrom(type.getRawType());
        }
    }

    /** Registers RockService instances under every contract interface they implement. */
    public static final class ServiceRegistrationListener implements TypeListener {
        private final ServiceRegistry registry;

        public ServiceRegistrationListener(ServiceRegistry registry) {
            this.registry = Objects.requireNonNull(registry);
        }

        @Override
        public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
            if (!RockService.class.isAssignableFrom(type.getRawType())) {
                return;
            }
            encounter.register((InjectionListener<I>) instance -> registerContracts((RockService) instance));
        }

        @SuppressWarnings("unchecked")
        private void registerContracts(RockService instance) {
            for (Class<?> iface : instance.getClass().getInterfaces()) {
                if (iface != RockService.class && RockService.class.isAssignableFrom(iface)) {
                    ((ServiceRegistry) registry)
                            .replace((Class<RockService>) iface, instance);
                }
            }
        }
    }

    /** Feeds LifecycleAware instances to the LifecycleManager in creation order. */
    public static final class LifecycleListener implements TypeListener {
        private final LifecycleManager lifecycleManager;

        public LifecycleListener(LifecycleManager lifecycleManager) {
            this.lifecycleManager = Objects.requireNonNull(lifecycleManager);
        }

        @Override
        public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
            if (!LifecycleAware.class.isAssignableFrom(type.getRawType())) {
                return;
            }
            encounter.register((InjectionListener<I>) instance ->
                    lifecycleManager.discovered((LifecycleAware) instance));
        }
    }
}
