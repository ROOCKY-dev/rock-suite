# ROCK Dependency Injection Specification (DIS)

**Document ID:** DIS-001
**Version:** 0.1
**Status:** Approved Foundation Document
**Resolves:** Critical Issue C-3 from ROCK Documentation Review

---

# 1. Purpose

This document resolves the unresolved DI container decision and defines how dependency injection is used throughout the ROCK platform.

---

# 2. Decision

## DI Library: Google Guice

```text
Library:     Google Guice
Version:     7.x (latest stable)
Coordinates: com.google.inject:guice:7.0.0
```

---

## Why Guice?

The decision comes down to one requirement that eliminates other options:

**ROCK modules are loaded dynamically at runtime.**

---

### Dagger 2 is eliminated

Dagger generates binding code at compile time. It cannot create new bindings for modules that are discovered and loaded at runtime. ROCK requires a DI container that can accept new bindings after JVM startup.

---

### Spring DI is eliminated

Too heavy. Classpath scanning, application contexts, and the Spring lifecycle are fundamentally incompatible with the Minecraft server environment. The overhead is unacceptable.

---

### Custom container is rejected

Engineering cost. The DI container is infrastructure, not a differentiator. Guice is mature and battle-tested. Building a custom container delays Alpha without providing any unique value.

---

### Guice wins

* Runtime binding creation ✅ — required for dynamic module loading
* Annotation-based (`@Inject`, `@Singleton`, `@Provides`) ✅
* Mature, well-documented ✅
* Zero XML configuration ✅
* Excellent testing support (`Modules.override()`) ✅
* Minecraft ecosystem precedent (Paper, several large Bukkit plugins) ✅
* Lightweight relative to Spring ✅

---

# 3. Architecture Overview

ROCK uses a two-level injector hierarchy:

```text
┌─────────────────────────────────────────┐
│         Platform Injector               │
│   (created by rock-core at boot)        │
│                                         │
│  Binds: CoreServices, DataService,      │
│         EventBus, Scheduler,            │
│         ConfigEngine, PermissionEngine  │
└─────────────────┬───────────────────────┘
                  │ child injectors created per module
        ┌─────────▼──────────┐  ┌─────────────────────┐
        │  Claims Injector   │  │  Economy Injector   │
        │  (child of         │  │  (child of          │
        │   platform)        │  │   platform)         │
        │                    │  │                      │
        │  Binds: Claims-    │  │  Binds: Economy-    │
        │  specific services │  │  specific services  │
        └────────────────────┘  └─────────────────────┘
```

Child injectors inherit all platform bindings.

Platform injector cannot access module-level bindings.

---

# 4. Platform Module Definition

`rock-core` defines the platform Guice module:

```java
public final class RockPlatformModule extends AbstractModule {

    @Override
    protected void configure() {
        // Core services — singletons
        bind(EventBus.class).to(DefaultEventBus.class).in(Scopes.SINGLETON);
        bind(ServiceRegistry.class).to(DefaultServiceRegistry.class).in(Scopes.SINGLETON);
        bind(Scheduler.class).to(RockScheduler.class).in(Scopes.SINGLETON);
        bind(ConfigEngine.class).to(TomlConfigEngine.class).in(Scopes.SINGLETON);
        bind(PermissionEngine.class).to(DefaultPermissionEngine.class).in(Scopes.SINGLETON);
        bind(LifecycleManager.class).to(DefaultLifecycleManager.class).in(Scopes.SINGLETON);
    }

    @Provides
    @Singleton
    DataService provideDataService(HikariDataSource dataSource) {
        return new JdbiDataService(dataSource);
    }

    @Provides
    @Singleton
    HikariDataSource provideDataSource(RockConfig config) {
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl(config.database().url());
        hikari.setMaximumPoolSize(config.database().pool().maximumSize());
        return new HikariDataSource(hikari);
    }
}
```

---

# 5. Module-Level Guice Modules

Each ROCK module provides its own Guice module:

```java
// In rock-claims
public final class ClaimsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ClaimService.class).to(DefaultClaimService.class).in(Scopes.SINGLETON);
        bind(ClaimRepository.class).to(JdbiClaimRepository.class).in(Scopes.SINGLETON);
    }
}
```

The module's child injector is created by the ModuleLoader:

```java
// In rock-core ModuleLoader
Injector moduleInjector = platformInjector.createChildInjector(
    rockModule.getGuiceModule()
);
```

---

# 6. Scope Definitions

## Singleton

One instance for the lifetime of the application.

Used for: all services, repositories, engines, registry.

```java
bind(ClaimService.class)
    .to(DefaultClaimService.class)
    .in(Scopes.SINGLETON);
```

---

## Request Scope (for Web Platform)

One instance per incoming HTTP request.

Used by: web dashboard handlers, REST API controllers.

```java
// Applied in web platform module
bind(RequestContext.class).in(RequestScoped.class);
```

---

## Command Scope

One instance per command execution.

Used by: command handlers that need per-execution state.

```java
// Applied per command dispatch
bind(CommandContext.class).in(CommandScoped.class);
```

This resolves the ambiguous "per operation" scope in earlier documents.

---

## Prototype

New instance per injection point.

Used sparingly. Only for objects that must not be shared.

```java
bind(ClaimBuildSession.class).in(Scopes.NO_SCOPE);
```

---

# 7. Injection Patterns

## Constructor Injection (Preferred)

```java
public final class DefaultClaimService implements ClaimService {

    private final ClaimRepository repository;
    private final EventBus eventBus;
    private final PermissionEngine permissions;

    @Inject
    public DefaultClaimService(
        ClaimRepository repository,
        EventBus eventBus,
        PermissionEngine permissions
    ) {
        this.repository = repository;
        this.eventBus = eventBus;
        this.permissions = permissions;
    }
}
```

Constructor injection is required for all services and repositories.

---

## Field Injection (Discouraged)

```java
// Avoid this
@Inject
private ClaimRepository repository;
```

Field injection makes unit testing harder. Use constructor injection.

Exception: legacy compatibility code only.

---

## Provider Injection

When lazy initialisation is needed:

```java
@Inject
Provider<ExpensiveService> expensiveService;

// Resolved only when get() is called
expensiveService.get().doWork();
```

---

# 8. Service Registration via DI

ROCK's `ServiceRegistry` integrates with Guice through a post-injection listener:

```java
public class ClaimsModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(ClaimService.class)
            .to(DefaultClaimService.class)
            .in(Scopes.SINGLETON);

        // Automatically registers with ServiceRegistry after creation
        bindListener(
            Matchers.subclassesOf(RockService.class),
            new ServiceRegistrationListener()
        );
    }
}
```

This means: any class implementing `RockService` is automatically registered in the `ServiceRegistry` when Guice creates it. Modules do not need manual registration code.

---

# 9. Anti-Patterns

### Anti-Pattern 1 — Static Access

```java
// FORBIDDEN
RockPlatform.getInstance().getClaimService()
```

Use injection. Static access defeats the entire purpose of DI and makes testing impossible.

---

### Anti-Pattern 2 — New Construction

```java
// FORBIDDEN
ClaimService service = new DefaultClaimService();
```

Use injection. Direct construction bypasses scope management and creates untestable code.

---

### Anti-Pattern 3 — Injector.getInstance() in Business Logic

```java
// FORBIDDEN in business code
injector.getInstance(ClaimService.class)
```

The injector itself should only be accessed during bootstrap. Business logic uses injected dependencies.

Acceptable only in: ModuleLoader bootstrap, test setup code.

---

### Anti-Pattern 4 — Cross-Module Injection

```java
// FORBIDDEN — Economy module binding into Claims injector
Injector claimsInjector = platformInjector.createChildInjector(
    claimsModule.getGuiceModule(),
    economyModule.getGuiceModule()  // ← cross-module contamination
);
```

Modules communicate through `ServiceRegistry` and the `EventBus`. They do not share injectors.

---

# 10. Testing with Guice

## Unit Tests — No Guice Required

Services are instantiated directly with mocked dependencies:

```java
@ExtendWith(MockitoExtension.class)
class DefaultClaimServiceTest {

    @Mock ClaimRepository repository;
    @Mock EventBus eventBus;
    @Mock PermissionEngine permissions;

    DefaultClaimService service;

    @BeforeEach
    void setUp() {
        service = new DefaultClaimService(repository, eventBus, permissions);
    }
}
```

---

## Integration Tests — Override Module

Use `Modules.override()` to swap real implementations for test doubles:

```java
Injector testInjector = Guice.createInjector(
    Modules.override(new RockPlatformModule())
           .with(new TestPlatformModule())  // overrides DataService with in-memory impl
);
```

---

## Test Module Example

```java
public final class TestPlatformModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(DataService.class)
            .to(InMemoryDataService.class)
            .in(Scopes.SINGLETON);

        bind(EventBus.class)
            .to(SynchronousEventBus.class)   // no async complexity in tests
            .in(Scopes.SINGLETON);
    }
}
```

---

# 11. Lifecycle Integration

Guice-managed services must integrate with the ROCK lifecycle.

Services implementing `LifecycleAware` are automatically registered for lifecycle callbacks:

```java
public interface LifecycleAware {
    void onEnable();
    void onDisable();
}

@Singleton
public class DefaultClaimService implements ClaimService, LifecycleAware {

    @Override
    public void onEnable() {
        // load cached claims, start scheduled tasks
    }

    @Override
    public void onDisable() {
        // flush pending writes, cancel tasks
    }
}
```

The `LifecycleManager` in rock-core discovers all `LifecycleAware` bindings via a Guice type listener and invokes them in correct order.

---

# 12. Summary

| Decision | Choice | Reason |
|----------|--------|--------|
| DI library | Google Guice 7.x | Only option supporting runtime dynamic module loading |
| Injector model | Two-level hierarchy (platform + per-module child) | Module isolation without losing platform service access |
| Scope: services/repos | Singleton | One instance, correct for stateless services |
| Scope: web handlers | Request-scoped | One instance per HTTP request |
| Scope: command handlers | Command-scoped | One instance per command execution |
| Preferred injection style | Constructor injection | Testability |
| Cross-module comms | ServiceRegistry + EventBus | Not direct DI bindings |
