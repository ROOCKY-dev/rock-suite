# ROCK Platform Specification (RPS)

**Document ID:** RPS-001
**Status:** Approved Foundation Specification

---

# 1. Platform Definition

ROCK is a modular service platform for Minecraft server management.

The platform consists of:

```text
┌─────────────────────────┐
│     ROCK Modules        │
├─────────────────────────┤
│      ROCK Services      │
├─────────────────────────┤
│       ROCK Core         │
├─────────────────────────┤
│    Loader Adapters      │
├─────────────────────────┤
│ Fabric / NeoForge/etc   │
└─────────────────────────┘
```

---

# 2. Platform Components

ROCK Core contains:

```text
Rock Core
│
├── Module Loader
├── Service Registry
├── Dependency Injection
├── Event Bus
├── Scheduler
├── Config Engine
├── Data Engine
├── Security Engine
├── Permission Engine
├── Metrics Engine
└── Lifecycle Manager
```

Everything else builds on top of these.

---

# 3. Module Loader

## Purpose

Discover, validate, load, and manage ROCK modules.

---

## Responsibilities

* Module discovery
* Dependency validation
* Version validation
* Startup ordering
* Shutdown ordering
* Hot reload support (future)

---

## Module Manifest

Every module must provide:

```toml
id = "rock-claims"

name = "Rock Claims"

version = "1.0.0"

api_version = "1.0"

authors = ["Ahmed"]

dependencies = [
  "rock-core",
  "rock-data"
]
```

---

## Module States

```text
DISCOVERED
↓
VALIDATED
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

# 4. Dependency Injection Container

## Purpose

Manage object creation.

Avoid:

```java
new ClaimService()
```

everywhere.

---

Instead:

```java
@Inject
ClaimService claimService;
```

---

## Supported Scopes

### Singleton

One instance.

Example:

```text
ClaimService
```

---

### Scoped

Per operation.

---

### Prototype

New instance each request.

---

# 5. Service Registry

## Purpose

Platform-wide service discovery.

---

## Example

Provider:

```java
registerService(
  ClaimService.class,
  claimService
);
```

Consumer:

```java
ClaimService claims;
```

---

## Rule

Modules communicate through services.

Never through implementation classes.

---

# 6. Event Bus

Probably the second most important subsystem.

---

## Purpose

Decouple modules.

---

Example:

```text
Player joins
```

Claims doesn't care.

Economy doesn't care.

Discord doesn't care.

---

Instead:

```text
PlayerJoinEvent
```

is emitted.

Modules react independently.

---

## Event Types

### Platform Events

```text
ModuleLoadedEvent
ModuleStartedEvent
```

---

### Player Events

```text
PlayerJoinEvent
PlayerQuitEvent
```

---

### Economy Events

```text
TransactionEvent
```

---

### Claim Events

```text
ClaimCreatedEvent
ClaimDeletedEvent
```

---

# 7. Scheduler

## Purpose

Manage tasks safely.

---

## Task Types

### Sync

Minecraft thread.

---

### Async

Worker thread.

---

### Delayed

Run later.

---

### Repeating

Run periodically.

---

Example:

```java
scheduler.runAsync(...)
```

---

## Rule

Database operations must use async tasks.

---

# 8. Configuration Engine

One of the most overlooked systems.

---

## Supported Formats

Primary:

```text
TOML
```

---

Future:

```text
YAML
JSON
```

---

## Features

* Validation
* Comments
* Auto generation
* Migration support
* Reload support

---

Example:

```toml
claims.max-size = 5000
claims.tax-rate = 5
```

---

# 9. Data Engine

## Purpose

Unified persistence layer.

---

Modules must not care whether storage is:

```text
SQLite
PostgreSQL
MariaDB
```

---

## Data Flow

```text
Module
↓
Repository
↓
Data Engine
↓
Database Driver
```

---

# 10. Repository Pattern

Required.

---

Example:

```java
ClaimRepository
```

instead of

```java
SELECT * FROM claims
```

inside modules.

---

Benefits:

* Testability
* Portability
* Cleaner code

---

# 11. Security Engine

## Purpose

Central security services.

---

Provides:

```text
Authentication
Authorization
Token Management
Session Management
Encryption Utilities
```

---

Used primarily by:

* Web Dashboard
* REST API
* Future Marketplace

---

# 12. Permission Engine

Separate from Permissions Module.

Important distinction.

---

Engine:

```text
Evaluates permissions
```

Module:

```text
Stores permissions
```

---

This allows future permission providers.

---

Example:

```java
permissionService.has(
  player,
  "rock.claims.create"
);
```

---

# 13. Metrics Engine

## Purpose

Observability.

---

Tracks:

```text
TPS Impact
Memory Usage
Database Queries
API Requests
Event Throughput
```

---

Future dashboard integration.

---

# 14. Logging Engine

Platform-wide logging.

---

Format:

```json
{
  "module":"claims",
  "level":"INFO",
  "message":"Claim created"
}
```

---

Centralized.

Searchable.

Exportable.

---

# 15. Lifecycle Manager

Controls startup order.

---

Example:

```text
rock-data
↓
rock-permissions
↓
rock-economy
↓
rock-claims
```

---

Prevents dependency failures.

---

# 16. Loader Abstraction Layer

The heart of cross-platform support.

---

Provides:

```text
RockPlayer
RockWorld
RockServer
RockItem
RockLocation
RockChunk
```

---

Modules see only ROCK abstractions.

Never Fabric classes.

Never NeoForge classes.

---

# 17. Networking Layer

Future-proofing.

---

Provides:

```text
Internal Messaging
WebSocket Support
Cluster Messaging
```

---

Needed later for:

* Proxy networks
* Multi-server economies
* Shared claims

---

# 18. Module Capability System

A powerful future feature.

Instead of:

```java
if (economyInstalled)
```

Use:

```java
Capability<EconomyService>
```

---

Allows optional integrations.

---

Example:

```text
Claims works
with or without Economy.
```

---

# 19. Extension API

Third-party developers need first-class support.

---

Provides:

```text
Events
Services
Repositories
Configuration
Permissions
Commands
```

---

Goal:

Building a ROCK extension should feel like building a Spring Boot application.

---

# 20. Command Framework

Centralized.

---

Provides:

```text
Commands
Subcommands
Permissions
Autocomplete
Localization
```

---

Example:

```text
/rock claims create
```

---

Not:

```text
/claims
/economy
/perm
```

all with different behavior.

---

# 21. Localization Framework

All user-facing text externalized.

---

Example:

```properties
claim.created=Claim created successfully
```

---

Allows:

```text
English
Arabic
French
German
Japanese
```

---

# 22. Platform Startup Sequence

```text
Boot
↓
Load Config
↓
Initialize Core
↓
Initialize Services
↓
Initialize Data
↓
Load Modules
↓
Start Event Bus
↓
Enable Commands
↓
Ready
```

---

# 23. Platform Shutdown Sequence

```text
Disable Modules
↓
Flush Events
↓
Save Data
↓
Close Connections
↓
Shutdown Services
↓
Terminate
```

---

# 24. Failure Recovery

If a module fails:

```text
Claims Crash
```

Desired outcome:

```text
Claims Disabled
Economy Running
Permissions Running
Server Running
```

Not:

```text
Entire server crashes
```

---

# 25. Core Architectural Rule

Every future design decision should answer:

> Does this belong in Core, a Service, a Domain, or a Module?

If the answer is unclear, it probably doesn't belong in Core.

Core must remain small.

A bloated Core eventually becomes impossible to evolve.
