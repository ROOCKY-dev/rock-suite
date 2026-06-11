# ROCK SUITE

Cross-platform Minecraft server management platform (targeting 1.21.11+),
built platform-first: one API, one data layer, one permission model, one
administrative experience — across Fabric and NeoForge.

**Version:** 1.0.0 · **License:** Apache-2.0 · **Java:** 21 LTS · **Build:** Gradle (Kotlin DSL)

## Repository layout

```
platform/   rock-api (zero-dependency contracts) · rock-core (DI, events, lifecycle,
            config, commands) · rock-data (JDBI3 + HikariCP + Flyway, async DataService)
modules/    rock-permissions · rock-claims · rock-economy · rock-discord
loaders/    rock-loader-fabric · rock-loader-neoforge · loader-stubs (compile-time API mirror)
tooling/    rock-conventions (Gradle convention plugins)
infrastructure/ci/  build.yml · release.yml (GitHub Actions)
DOCS/       foundation documents + ARCHITECTURAL_REVIEW.md
```

## Architecture in one paragraph

Everything integrates through `rock-api`, which depends on nothing but the
JDK — enforced by the build. `rock-core` boots a Guice `Stage.PRODUCTION`
platform injector (`requireExplicitBindings`, no AOP) and gives each feature
module its own child injector; modules communicate only via the
`ServiceRegistry` and the priority-ordered `EventBus`. All database access
goes through the async `DataService` (virtual threads) — blocking the tick
thread on I/O is impossible to express. Loader-specific imports exist only
under `loaders/`, verified by the `verifyLoaderIsolation` check on every build.

## Build

```bash
./gradlew build          # compile + tests + architecture checks, all projects
./gradlew :rock-api:build
```

## Notes for packagers

Loader adapters compile against minimal API stubs (`loaders/loader-stubs`).
Producing runnable mod jars additionally requires the loader toolchains
(Fabric Loom / NeoForge ModDevGradle) for remapping and jar-in-jar bundling of
the platform + Guice; that is a release-packaging step, not a platform
concern. See `DOCS/ARCHITECTURAL_REVIEW.md` §C-2.
