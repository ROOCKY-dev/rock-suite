plugins {
    id("dev.rock.java-conventions")
}

// Compile-time-only stubs mirroring the minimal slice of the Fabric, NeoForge,
// and Minecraft APIs the loader adapters touch (Architectural Review C-2: the
// API-jar pattern). This project is NEVER published or shipped — at packaging
// time the real loader toolchains (Loom / ModDevGradle) provide these classes.
// It lives under loaders/ because only loaders/ may contain these packages.
