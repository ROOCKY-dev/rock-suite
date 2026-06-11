# ROCK Architecture Vision Document (AVD)

## 1. Purpose

This document defines the architectural foundations of ROCK SUITE.

It establishes the rules, principles, and core systems that all current and future ROCK modules must follow.

The objective is to ensure:

* Cross-loader compatibility
* Modular development
* Long-term maintainability
* Third-party extensibility
* Unified administration

---

# 2. Architectural Philosophy

## Traditional Minecraft Approach

```text
Plugin A
 ├─ Config
 ├─ Database
 └─ Commands

Plugin B
 ├─ Config
 ├─ Database
 └─ Commands

Plugin C
 ├─ Config
 ├─ Database
 └─ Commands
```

Everything is isolated.

Integration requires bridges.

---

## ROCK Approach

```text
ROCK Platform

├── API
├── Event Bus
├── Service Registry
├── Data Layer
├── Permission Layer
├── Web Layer
├── Network Layer
└── Loader Layer

Modules

├── Claims
├── Economy
├── Permissions
├── Logging
├── Discord
├── Admin
└── Web
```

All modules communicate through platform services.

---

# 3. Core Principles

## Principle 1

No module may directly depend on another module.

Allowed:

```text
Claims → Rock API
Economy → Rock API
Discord → Rock API
```

Forbidden:

```text
Claims → Economy
Discord → Claims
Permissions → Economy
```

---

## Principle 2

All shared functionality is exposed as services.

Example:

```java
ClaimService
EconomyService
PermissionService
PlayerService
```

---

## Principle 3

All communication occurs through:

* Service calls
* Events

Never direct implementation references.

---

# 4. Layered Architecture

```text
Applications
      │
Modules
      │
Rock Services
      │
Rock Core
      │
Loader Adapters
      │
Fabric / Forge / NeoForge / Paper
```

---

# 5. Core Components

## Rock Core

Responsibilities:

* Module lifecycle
* Dependency injection
* Event bus
* Service registry
* Scheduler
* Configuration system

Required by every module.

---

## Rock API

Public interfaces only.

Contains:

```java
Player
Claim
Rank
Permission
EconomyAccount
Server
Location
World
```

No loader-specific code allowed.

---

## Loader Adapter

Responsibilities:

* Translate Minecraft internals into ROCK abstractions.

Example:

```java
FabricPlayer
↓
RockPlayer
```

```java
NeoForgePlayer
↓
RockPlayer
```

```java
ForgePlayer
↓
RockPlayer
```

---

# 6. Module Lifecycle

Every module follows the same lifecycle.

```text
DISCOVERED
    ↓
LOADED
    ↓
INITIALIZED
    ↓
RUNNING
    ↓
STOPPING
    ↓
UNLOADED
```

---

## Module Interface

Example:

```java
public interface RockModule {

    void onLoad();

    void onEnable();

    void onDisable();

}
```

---

# 7. Dependency Injection

No singleton abuse.

No static managers.

No global access patterns.

Services are requested through injection.

Example:

```java
public class ClaimManager {

    private final EconomyService economy;

}
```

---

# 8. Service Registry

Services register themselves.

Example:

```java
serviceRegistry.register(
    ClaimService.class,
    claimService
);
```

Consumers request:

```java
ClaimService service;
```

not

```java
new ClaimService();
```

---

# 9. Event Architecture

Everything emits events.

---

## Example Events

### Player Events

```text
PlayerJoinEvent
PlayerLeaveEvent
PlayerDeathEvent
```

---

### Claims Events

```text
ClaimCreatedEvent
ClaimDeletedEvent
ClaimTransferredEvent
```

---

### Economy Events

```text
BalanceChangedEvent
TransactionCreatedEvent
```

---

### Permission Events

```text
RankAssignedEvent
PermissionGrantedEvent
```

---

# 10. Data Architecture

Single platform-wide data system.

Modules never directly manage database connections.

---

## Database Layer

```text
Rock Data
```

provides:

```java
DataService
```

Modules use:

```java
dataService.save(...)
```

not:

```java
DriverManager.getConnection(...)
```

---

# 11. Storage Targets

Supported:

### SQLite

Default

### PostgreSQL

Recommended

### MariaDB

Optional

Future:

### MongoDB

Under evaluation

---

# 12. Unified Identity Model

Every user has one identity.

```java
RockPlayer
```

Contains:

```java
UUID
Username
Metadata
Ranks
Claims
Economy
History
```

This becomes the foundation of the entire platform.

---

# 13. Permissions Model

Not tied to a specific module.

Everything uses:

```text
rock.*
```

Examples:

```text
rock.claims.create
rock.claims.delete

rock.economy.deposit
rock.economy.withdraw

rock.admin.reload
```

---

# 14. Web Architecture

Long-term flagship component.

---

## Web Services

Expose:

* Player Profiles
* Claims Maps
* Logs
* Economy
* Administration

---

## API Types

### REST

Primary

### WebSocket

Real-time updates

---

# 15. Security Model

Every action must be auditable.

---

## Audit Events

Examples:

```text
Player Banned

Claim Deleted

Money Transferred

Permission Granted
```

---

All produce log entries.

---

# 16. Third-Party Development

External developers become first-class citizens.

---

## SDK

Future:

```text
Rock SDK
```

Provides:

* APIs
* Documentation
* Templates
* Testing Framework

---

# 17. Future Loader Targets

Phase 1:

* Fabric
* NeoForge
* Forge

Phase 2:

* Paper

Phase 3:

* Velocity
* Folia

---

# 18. Architectural Rule Zero

The most important rule in the entire project:

> If a feature requires bypassing Rock API to access loader-specific functionality, then Rock API is missing an abstraction and must be expanded.

Never allow modules to "just use Fabric directly."

The moment that happens, the architecture begins to decay.

---
