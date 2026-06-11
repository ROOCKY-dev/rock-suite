package dev.rock.api.event;

import java.util.concurrent.atomic.AtomicBoolean;

/** Base implementation for cancellable events. */
public abstract class AbstractCancellable implements Cancellable {

    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    @Override
    public final boolean cancelled() {
        return cancelled.get();
    }

    @Override
    public final void cancel() {
        cancelled.set(true);
    }
}
