package dev.rock.api.module;

/**
 * Contract implemented by every ROCK module (AVD §6). The platform drives the
 * lifecycle; modules never self-start. The returned Guice module is installed
 * into a child injector of the platform injector (DIS §3) — modules never share
 * injectors with each other.
 */
public interface RockModule {

    ModuleManifest manifest();

    /**
     * The module's Guice bindings, installed into the module's child injector.
     * Returned as {@link Object} to keep rock-api free of the Guice dependency;
     * the platform casts to {@code com.google.inject.Module}.
     */
    Object guiceModule();

    /** Called after the module jar is loaded, before injection. No services available yet. */
    default void onLoad() {
    }

    /** Called once the module's injector is built and services are registered. */
    void onEnable();

    /** Called on shutdown or unload; must release resources and close subscriptions. */
    void onDisable();
}
