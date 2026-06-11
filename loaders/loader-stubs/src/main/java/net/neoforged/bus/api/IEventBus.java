package net.neoforged.bus.api;

import java.util.function.Consumer;

/** STUB — compile-time mirror of the NeoForge event bus. */
public interface IEventBus {

    <T extends Event> void addListener(Consumer<T> listener);
}
