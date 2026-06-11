package dev.rock.api.lifecycle;

/**
 * Services implementing this interface are discovered by the platform's
 * LifecycleManager via a Guice type listener and receive callbacks in
 * registration order on enable and reverse order on disable (DIS §11).
 */
public interface LifecycleAware {

    void onEnable();

    void onDisable();
}
