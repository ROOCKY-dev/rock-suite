package dev.rock.api.event;

/**
 * Handle to an active event subscription. Closing it unregisters the listener;
 * modules must close their subscriptions on disable.
 */
public interface Subscription extends AutoCloseable {

    boolean active();

    @Override
    void close();
}
