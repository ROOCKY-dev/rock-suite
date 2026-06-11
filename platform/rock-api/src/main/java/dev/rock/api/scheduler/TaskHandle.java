package dev.rock.api.scheduler;

/** Handle to a scheduled task. */
public interface TaskHandle {

    boolean cancelled();

    void cancel();
}
