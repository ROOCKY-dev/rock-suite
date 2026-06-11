package dev.rock.api.module;

/** Module lifecycle states (RPS §3, AVD §6). */
public enum ModuleState {
    DISCOVERED,
    VALIDATED,
    LOADED,
    INITIALIZED,
    RUNNING,
    STOPPING,
    UNLOADED,
    /** Crash isolation: a failed module is disabled, the server keeps running (TRS §17). */
    FAILED
}
