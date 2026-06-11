# ROCK Engineering Handbook (REH)

**Version:** 2.0
**Status:** Approved Planning Document

---

# 1. Engineering Philosophy

ROCK follows four fundamental principles:

### Platform First

Everything is built on top of ROCK Core.

---

### Domain Driven

Code is organised around business domains.

Not around technical utilities.

---

### Loader Independent

Modules must not know what loader is running.

---

### Service Oriented

Communication occurs through services and events.

Not direct implementation references.

---

# 2. Repository Strategy

## Decision

Use a **Monorepo**.

---

### Why?

ROCK is fundamentally one platform.

Benefits:

* Easier refactoring across module boundaries
* Unified CI/CD pipeline
* Atomic changes (one commit can update API + implementation)
* Shared versioning
* Simpler dependency management
* Shared tooling and conventions

---

### Structure

```text
rock-suite/
│
├── platform/           ← foundational libraries (see Section 3)
├── modules/            ← feature modules (see Section 5)
├── loaders/            ← loader adapters (see Section 4)
├── tooling/            ← Gradle convention plugins, custom build tooling (see Section 3a)
├── docs/               ← all project documentation
├── examples/           ← example/template modules for developers
└── infrastructure/     ← CI/CD, Docker, deployment configs (see Section 3b)
```

---

# 3. Platform Layer

```text
platform/
│
├── rock-api
├── rock-core
├── rock-data
├── rock-events
├── rock-security
├── rock-config
├── rock-command
├── rock-metrics
└── rock-testing
```

These are foundational libraries.

---

## rock-api

Contains:

```text
Domain Models
Public Interfaces
Events
Contracts
```

No implementation code.

Zero external dependencies (other than Java 21 standard library).

This is the most important rule: any import added to rock-api is forced onto every module.

---

## rock-core

Contains:

```text
Lifecycle Manager
DI Container (Guice)
Service Registry
Scheduler
Bootstrap
```

---

## rock-data

Contains:

```text
Repositories
Storage Abstractions
Migration Engine (Flyway)
HikariCP Connection Pool
JDBI3 Bindings
Database Drivers
```

---

# 3a. Tooling Directory

```text
tooling/
│
├── rock-conventions/        ← Gradle convention plugins
│   ├── java-conventions     ← standard Java setup (Java 21, Checkstyle, Spotless)
│   ├── module-conventions   ← standard ROCK module build setup
│   └── publish-conventions  ← Maven Central / GitHub Packages publishing config
├── checkstyle/
│   └── rock-checkstyle.xml  ← ROCK-specific Checkstyle rules
└── spotless/
    └── rock-format.xml      ← Spotless formatting config
```

All modules apply the convention plugins instead of duplicating build config:

```kotlin
// modules/rock-claims/build.gradle.kts
plugins {
    id("dev.rock.module-conventions")
}
```

---

# 3b. Infrastructure Directory

```text
infrastructure/
│
├── ci/
│   ├── build.yml            ← main CI pipeline (GitHub Actions)
│   ├── release.yml          ← release pipeline
│   └── security.yml         ← dependency audit and SAST scan
├── docker/
│   ├── test-server/         ← Dockerised Minecraft test server for integration tests
│   └── docker-compose.yml   ← local dev environment (server + postgres + redis)
└── scripts/
    ├── benchmark.sh         ← runs Spark-based performance benchmarks
    └── release.sh           ← version bump + changelog generation
```

---

# 4. Loader Layer

```text
loaders/
│
├── rock-loader-fabric
├── rock-loader-neoforge
├── rock-loader-paper        ← Phase 2
└── rock-loader-velocity     ← Phase 2
```

Only these projects may directly access loader APIs.

---

## Critical Rule

No module may import:

```java
net.fabricmc.*
net.neoforged.*
org.bukkit.*
```

Directly.

Ever.

Violations are caught by Checkstyle rules in the `java-conventions` plugin.

---

# 5. Module Layer

```text
modules/
│
├── rock-claims
├── rock-economy
├── rock-permissions
├── rock-logging
├── rock-discord
├── rock-admin
├── rock-web
└── rock-chat
```

Modules are products.

Platform projects are infrastructure.

---

# 6. Package Naming Standard

All official code:

```java
dev.rock.*
```

---

Examples:

```java
dev.rock.api
dev.rock.claims
dev.rock.economy
```

---

Avoid:

```java
com.ahmed.*
org.example.*
```

The platform has a single identity.

---

# 7. Dependency Rules

**Allowed:**

```text
Module → rock-api
Module → rock-data (via DataService abstraction only)
```

**Forbidden:**

```text
Claims → Economy (direct module import)
Discord → Claims (direct module import)
```

---

**Correct pattern for cross-module communication:**

```text
Claims                      Economy
  │                            │
  ↓                            ↓
EconomyService (rock-api)   ClaimService (rock-api)
  ↑                            ↑
  └──── resolved at runtime via Service Registry ────┘
```

---

# 8. Dependency Graph

This graph shows **"depends on" relationships** (arrows point toward the dependency).

```text
┌─────────────────────────────────────────┐
│              rock-api                    │
│        (zero dependencies)              │
└──────────┬──────────────────────────────┘
           │ implements / depends on
           ▼
┌─────────────────────────────────────────┐
│              rock-core                   │
│   (depends on: rock-api)                │
└──────────┬──────────────────────────────┘
           │ depends on
           ▼
┌─────────────────────────────────────────┐
│              rock-data                   │
│   (depends on: rock-api, rock-core)     │
└──────────┬──────────────────────────────┘
           │ consumed by
           ▼
┌─────────────────────────────────────────┐
│               modules                    │
│  (depend on: rock-api; may use          │
│   rock-data via DataService only)       │
└─────────────────────────────────────────┘
```

Loaders depend on rock-core to register themselves, but are independent of modules:

```text
┌─────────────────────────────────────────┐
│              loaders                     │
│   (depend on: rock-core, rock-api;      │
│    also import: Fabric / NeoForge APIs) │
└─────────────────────────────────────────┘
```

**Legend:**
```text
A ──depends on──▶ B    means A imports B
A ──implements──▶ B    means A implements interfaces defined in B
```

The key rule: **rock-api has no imports from anywhere in this graph.**
It is the foundation. All dependency arrows point toward it; none come out of it.

---

# 9. Build System

## Required

```text
Gradle (Kotlin DSL)
Java 21 LTS
```

---

Root structure:

```text
rock-suite/
├── build.gradle.kts         ← root build (applies to all subprojects)
├── settings.gradle.kts      ← declares all subprojects
├── gradle.properties        ← shared version catalog entries
└── gradle/
    ├── libs.versions.toml   ← version catalog (single source of truth for deps)
    └── wrapper/
        └── gradle-wrapper.properties
```

---

Use Kotlin DSL.

Avoid Groovy DSL.

Use Gradle version catalog (`libs.versions.toml`) for all dependency versions. No hardcoded version strings in module build files.

---

# 10. Versioning Strategy

Platform version:

```text
MAJOR.MINOR.PATCH
```

applies to the entire suite.

---

All official modules follow the platform version:

```text
ROCK Platform 2.3.0

rock-claims      2.3.0
rock-economy     2.3.0
rock-permissions 2.3.0
```

---

Third-party modules version independently.

---

# 11. CI/CD Architecture

## Platform

GitHub Actions.

---

## Every Commit

```text
Lint (Checkstyle + Spotless)
Compile
Unit Tests
Integration Tests (Testcontainers)
Security Scan (Dependency Check + CodeQL)
Coverage Report (JaCoCo)
Packaging
```

---

## Pull Requests to `develop`

All above checks must pass.

Coverage must not drop below tier thresholds.

---

## Releases

Triggered by pushing a version tag (`v1.2.3`).

```text
Full pipeline
Build artifacts
Publish to Maven Central
Publish to GitHub Releases
Generate changelog
```

---

Main branch must always be releasable.

---

# 12. Testing Strategy

Three layers.

---

## Unit Tests

Fast.

No Minecraft runtime.

No database.

Covers: domain logic, service logic, event handling, command parsing.

---

## Integration Tests

Uses Testcontainers for real database instances.

Covers:

* Repository implementations
* Service orchestration
* Event bus behaviour
* Migration scripts

---

## Loader Tests

Full server boot in a test environment.

```text
Fabric test server
NeoForge test server
```

Covers: module loading, event registration, command registration, full lifecycle.

---

# 13. Code Style Standards

Required:

```text
Google Java Style Guide
```

with ROCK amendments.

---

Tools:

```text
Spotless    ← automatic formatting on build
Checkstyle  ← style rule enforcement
ErrorProne  ← common bug pattern detection
SpotBugs    ← static analysis
```

---

Automatic formatting runs before compile. No style debates in code review.

---

# 14. Documentation Architecture

```text
docs/
│
├── architecture/            ← AVD, DMS, TRS, RPS, and this file
├── api/                     ← generated Javadoc (auto-published)
├── modules/                 ← per-module user documentation
├── guides/                  ← installation and admin guides
├── tutorials/               ← developer tutorials
└── governance/
    ├── CHARTER.md
    ├── GDPM.md
    └── CODE_OF_CONDUCT.md
```

---

Documentation is part of the product.

Not an afterthought.

---

# 15. Example Projects

```text
examples/
│
├── simple-module           ← minimal working module (hello world equivalent)
├── custom-command          ← registering a command through the command framework
├── custom-service          ← registering and consuming a service
├── custom-event            ← publishing and subscribing to events
└── custom-repository       ← implementing a repository with rock-data
```

These become the learning path for new module developers.

---

# 16. Release Channels

## Experimental

```text
0.x
```

No stability guarantees.

---

## Alpha

```text
1.0.0-alpha.1
```

Architecture proven. Core platform only.

---

## Beta

```text
1.0.0-beta.1
```

Feature modules available. Not production-recommended.

---

## Stable

```text
1.0.0
```

Production-ready.

---

# 17. Alpha Roadmap

The first deliverable must not be Claims.

It must not be Economy.

It must not be Discord.

---

It must be:

## ROCK Platform Alpha

Containing:

```text
rock-api
rock-core
rock-events
rock-config
rock-command
rock-loader-fabric
```

Only.

---

Goal:

**Prove the architecture works.**

Features come later.

---

# 18. Alpha Success Criteria

All of the following must work:

### Module Loading

```text
rock-test-module
```

loads and unloads through full lifecycle.

---

### Events

```text
PlayerJoinEvent
```

fires and is received by a registered listener.

---

### Commands

```text
/rock version
```

executes and returns the correct platform version.

---

### Services

```text
TestService
```

registers through DI and is resolved by a test consumer.

---

### Loader Isolation

```text
No net.fabricmc.* imports exist in rock-api, rock-core, or any module.
```

Verified by CI.

---

If these all pass:

Architecture validated.

---

# 19. Beta Roadmap

Add:

```text
rock-data        ← JDBI3 + HikariCP + Flyway
rock-permissions ← permission storage and evaluation
```

---

Why permissions first?

Because nearly every future module (claims, economy, discord, admin) needs to check permissions. They cannot be stable without a stable permission system beneath them.

---

# 20. Release Candidate Roadmap

```text
rock-claims      ← first major proof-of-value module
rock-economy     ← economy accounts and transactions
rock-logging     ← structured audit logging
rock-discord     ← Discord integration
rock-admin       ← in-game administration
rock-loader-neoforge   ← second loader proven
```

---

# 21. Release 1.0 Roadmap

```text
rock-web         ← web dashboard (ROCK's flagship feature)
Public API docs  ← full Javadoc + developer guides
SDK / MDK        ← module development kit
Maven Central publish ← open to third-party module authors
```

---

# 22. Release 2.0 Roadmap

```text
rock-loader-paper
rock-loader-velocity
```

---

Multi-server networking.

Proxy-wide economy.

Shared claims across server instances.

---

# 23. Artifact Publishing Strategy

## Official Releases

Published to:

```text
Maven Central
Group: dev.rock
```

---

## Snapshot / Pre-release

Published to:

```text
GitHub Packages
```

Pre-release coordinates:

```text
dev.rock:rock-api:1.0.0-alpha.1
```

---

## Third-Party Module Dependency

Module authors add to `build.gradle.kts`:

```kotlin
repositories {
    mavenCentral()
}

dependencies {
    compileOnly("dev.rock:rock-api:1.0.0")
}
```

---

## BOM (Bill of Materials)

A ROCK BOM artifact is published to simplify multi-module projects:

```kotlin
implementation(platform("dev.rock:rock-bom:1.0.0"))
compileOnly("dev.rock:rock-api")        // version from BOM
compileOnly("dev.rock:rock-command")    // version from BOM
```

---

# 24. Long-Term Vision

Five years out, the architecture should support:

```text
Official Modules
Community Modules
Marketplace
Multi-Server Networks
Web Administration
REST APIs
Cluster Synchronisation
WebSocket Real-Time Dashboard
```

Without requiring architectural rewrites.

The design decisions made during Alpha — DI container choice, event bus model, domain objects, loader abstraction — are the ones that will either enable or constrain every feature listed above.

---

# 25. Engineering Principle Zero

The most important engineering rule in the entire project:

> Build the platform that makes the modules easy.

Do not build modules and hope a platform emerges later.
