# ROCK SUITE — Architectural Review

**Author:** Principal Staff Engineer (autonomous build agent)
**Status:** Accepted — governs the v1.0.0 implementation
**Reviewed inputs:** CHARTER.md, TRS.md, DMS.md, REH.md, DIS.md, AVD.md, GDPM.md, RPS.md

---

## 1. Verdict

The specifications are coherent and the layering (api → core → data → modules, loaders
isolated) is sound. Five defects would have caused dead-ends during implementation.
Each is resolved below; the resolutions are binding for this codebase.

---

## 2. Defects Found and Resolutions

### D-1 (Critical): Circular dependency in DIS §4

`RockPlatformModule` is specified to live in `rock-core` yet directly constructs
`HikariDataSource` and `JdbiDataService` — classes that belong to `rock-data`.
REH §8 simultaneously declares `rock-data → rock-core`. That is a hard build cycle.

**Resolution — Contributed Platform Modules:**
The platform injector is created from a *list* of Guice modules assembled at bootstrap:

```java
Injector platform = Guice.createInjector(
    Stage.PRODUCTION,
    new RockCoreModule(),        // rock-core: EventBus, ServiceRegistry, Scheduler, Lifecycle, Config, Commands
    new RockDataModule(config)   // rock-data: HikariCP, JDBI3, Flyway, DataService
);
```

`rock-core` never references a `rock-data` class. The loader adapter (composition root)
is the only place both are named together.

### D-2: DIS §8 auto-registration listener does not compile

`bindListener(Matchers.subclassesOf(RockService.class), new ServiceRegistrationListener())`
mixes a `Matcher<Class>` with an API requiring `Matcher<TypeLiteral<?>>` and a
`TypeListener`. Installing it inside each feature module would also double-register
services in the shared registry.

**Resolution:** a single platform-level `TypeListener` + `InjectionListener`
(`ServiceRegistrationListener` in `rock-core`) installed exactly once in
`RockCoreModule`. Any instantiated type assignable to `RockService` is registered
automatically. Feature modules contain no registration code.

### D-3: Child-injector JIT binding leakage

Guice promotes just-in-time (implicit) bindings to the **parent** injector. With the
two-level hierarchy, a module-private class resolved implicitly would leak into the
platform injector and could collide with a sibling module's class of the same binding key.

**Resolution:** the platform injector calls `binder().requireExplicitBindings()` and is
created with `Stage.PRODUCTION`:

* No JIT bindings can be promoted to the platform level — module isolation is enforced
  by Guice itself, not convention.
* Eager singletons mean misconfiguration fails at boot, supporting the TRS 99.9%
  startup-success target.
* No AOP / method interception is used anywhere (see D-6 / classloading).

### D-4: TRS §5 DOWN migrations are unimplementable with Flyway OSS

Undo migrations are a Flyway Teams/Enterprise feature. The community artifact cannot
execute them; requiring "UP and DOWN procedures" for every migration is a dead-end.

**Resolution:**

* Forward-only `V###__name.sql` migrations remain the schema contract (Flyway OSS).
* Every migration ships a paired `U###__name.sql` undo script in
  `db/migration/undo/`, executed by `rock-data`'s `MigrationRollbackRunner` —
  transactional where the engine supports transactional DDL (PostgreSQL, SQLite);
  failures are logged and abort without partial application (single-transaction rule).
* Operational policy: backup before downgrade; documented in rock-data.

### D-5: DIS §5 contradicts TRS §5 on module data access

DIS shows `rock-claims` binding `JdbiClaimRepository`; TRS forbids SQL/JDBI access from
module code ("Modules SHALL communicate through DataService only").

**Resolution — async `DataService` as the only data contract:**

* `rock-api` defines `DataService` with **CompletableFuture-returning** methods and a
  neutral `RowView` mapping interface. Zero JDBI types in the contract.
* `rock-data` implements it with JDBI3 + HikariCP, executing on a **virtual-thread
  executor** (Java 21). The Minecraft tick thread never touches JDBC.
* Feature modules implement their repositories *on top of* `DataService`. `org.jdbi`
  imports exist only inside `rock-data`. Enforced by the build (see §4).

---

## 3. Consolidation Decisions

### C-1: No separate `rock-events` project

REH lists both "Events" inside `rock-api` and a standalone `rock-events` project — a
contradiction. Decision: event **contracts** (interfaces, records, priority enum)
live in `rock-api`; the bus **implementation** lives in `rock-core`. The config engine
(TOML) and command framework likewise fold into `rock-core` for v1.0.0, matching the
mission directive's module list. They can be extracted later without API breakage
because their contracts are already in `rock-api`.

### C-2: Loader classloading strategy

Fabric (Knot classloader) and NeoForge (module-layer / SecureJar isolation) both break
runtime bytecode generation. Therefore:

* **No Guice AOP or method interception anywhere.** Plain constructor bindings only.
* Guice + ROCK platform jars ship via jar-in-jar (Fabric nested jars / NeoForge jarJar).
* Loader adapters in this repo compile against **minimal `compileOnly` API stubs**
  located inside `loaders/` (the API-jar pattern). This keeps the monorepo build
  hermetic and fast. Full Loom / ModDevGradle remapping and runtime packaging is a
  release-pipeline packaging step, not a compile-time dependency of the platform.
* Mission-directive law preserved: `net.fabricmc.*` / `net.neoforged.*` imports exist
  **only** under `loaders/`.

### C-3: `rock-discord` transport

JDA would be the single heaviest dependency in the tree. v1.0.0 uses the JDK's
`java.net.http` client with a rate-limited, exponential-backoff message queue
(TRS §13 requirements met: queue required, never sends from the game thread, backoff
capped at 60 s). A gateway (WebSocket) transport can be added behind the same
`DiscordGateway` interface later.

---

## 4. Enforcement (architecture by build, not trust)

* `rock-api` declares **zero** dependencies; any addition fails review by diff.
* A `verifyLoaderIsolation` Gradle check (wired into `check` by the java-conventions
  plugin) fails the build if `net.fabricmc`, `net.neoforged`, or `org.bukkit` is
  imported outside `loaders/`, and if `org.jdbi` is imported outside `rock-data`.
* Constructor injection only; no `@Inject` fields in production code.
* All `DataService` operations are async by signature — blocking is impossible to
  express in module code.

## 5. Stack lock (per directive)

Java 21 LTS · Gradle Kotlin DSL (version catalog) · Guice 7.x · JDBI3 + HikariCP +
Flyway (forward-only) · TOML (tomlj in rock-core only) · JUnit 5 + Mockito · JaCoCo.
