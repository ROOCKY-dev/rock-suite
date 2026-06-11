package net.neoforged.bus.api;

/** STUB — compile-time mirror of NeoForge's cancellable event mixin interface. */
public interface ICancellableEvent {

    void setCanceled(boolean canceled);

    boolean isCanceled();
}
