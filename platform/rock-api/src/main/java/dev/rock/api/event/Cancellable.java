package dev.rock.api.event;

/**
 * Implemented by player-action events where intervention is meaningful
 * (TRS §8). A cancelled event at {@link EventPriority#FIRST} is not delivered
 * to later priorities unless the subscriber opted in to receive cancelled events.
 */
public interface Cancellable extends Event {

    boolean cancelled();

    void cancel();
}
