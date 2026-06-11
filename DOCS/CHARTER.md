# ROCK SUITE Project Charter

**Version:** 2.0
**Status:** Approved — Revised to align with REH Alpha Roadmap

---

## Project Name

ROCK SUITE

---

## Project Type

Cross-platform Minecraft Server Management Platform

---

## Technology Baseline

This platform is built on:

```text
Language:        Java 21 LTS
Build System:    Gradle (Kotlin DSL)
DI Container:    Google Guice
Data Access:     JDBI3 + HikariCP
Config Format:   TOML (primary)
Licensing:       Apache License 2.0
```

These are not subject to module-level override.

---

## Project Sponsor

Founding Developer Team

(Currently Ahmed)

---

## Project Vision

To create a unified, modular, cross-loader platform that provides all essential Minecraft server management functionality through a consistent architecture, shared data model, integrated web services, and a common API.

Rather than forcing server owners to assemble dozens of disconnected mods and plugins, ROCK SUITE will offer a cohesive ecosystem where administration, claims, economy, permissions, logging, Discord integration, and web management operate as parts of a single platform.

---

## Mission Statement

Reduce operational complexity for Minecraft server owners by replacing fragmented server tooling with a modular but integrated management platform.

---

## Strategic Objectives

### Objective 1

Prove the core architecture before building features.

Platform validation comes before Claims. Claims come before Economy. Economy comes before Discord.

### Objective 2

Provide feature parity with major server management solutions.

### Objective 3

Support multiple Minecraft ecosystems through abstraction layers.

Initial targets:

* Fabric
* NeoForge

Future targets:

* Legacy Forge (Phase 2, best-effort)
* Paper
* Velocity
* Folia

### Objective 4

Establish ROCK API as the primary integration point for all modules.

### Objective 5

Create a unified administrative experience.

### Objective 6

Minimise configuration duplication across modules.

---

## Initial Deliverables

Phases are sequential.

Later phases do not begin before earlier phases reach stable status.

---

### Phase Alpha — Prove the Architecture

**Goal:** Validate that the core platform works. No feature modules.

```text
rock-api             ← public interfaces and contracts
rock-core            ← DI, service registry, lifecycle, event bus, scheduler
rock-events          ← platform event definitions
rock-config          ← configuration engine (TOML)
rock-command         ← command framework
rock-loader-fabric   ← first loader adapter
```

**Alpha Success Criteria:**

* `rock-test-module` loads and unloads successfully
* `PlayerJoinEvent` fires and is received by a test listener
* `/rock version` executes correctly
* `TestService` registers and resolves through DI
* No loader-specific imports exist outside `rock-loader-fabric`

---

### Phase Beta — Data and Permissions

**Goal:** Prove the data layer and permission system.

```text
rock-data            ← repository layer, JDBI3, HikariCP, Flyway migrations
rock-permissions     ← permission storage and evaluation engine
```

**Note:** Nearly every future module depends on Permissions. It must be stable before Claims or Economy development begins.

---

### Phase Release Candidate — Core Feature Modules

**Goal:** First production-usable feature set.

```text
rock-claims          ← flagship domain module
rock-economy         ← economy accounts and transactions
rock-logging         ← structured audit logging
rock-discord         ← Discord integration
rock-admin           ← in-game administration tools
rock-loader-neoforge ← second loader adapter
```

---

### Phase 1.0 — Platform Release

**Goal:** Public-ready, documented, stable.

```text
rock-web             ← web dashboard
Public API docs      ← full developer documentation
SDK / MDK            ← module development kit for third parties
Stable loader support for Fabric + NeoForge
```

---

## Rough Timeline

These are targets, not guarantees.

```text
Phase Alpha:    Q3 2026
Phase Beta:     Q4 2026 – Q1 2027
Release Candidate: Q2 2027
Version 1.0:    Q4 2027
```

The most important milestone is Alpha. A working Alpha proves the architecture is sound before any significant feature investment.

---

## Success Criteria

**Technical:**

* Modules communicate exclusively through Rock API.
* Loader-specific code remains isolated in Loader Adapters.
* Successful deployment on Fabric and NeoForge.
* 80% test coverage on platform code; 70% on official modules.

**Product:**

* Server owners can install core modules in less than 15 minutes.
* Single configuration model across all modules.
* Unified administration experience.
* No separate databases per module.

**Community:**

* Third-party module development enabled via public SDK.
* Public API adoption by at least 3 external modules before 1.0.
* Community contribution framework established and documented.

---

# Business Case

## Current Industry Problem

Minecraft server administration is highly fragmented.

Server owners commonly depend on numerous independent plugins and mods that:

* Use different configuration formats.
* Maintain separate databases.
* Implement incompatible permission structures.
* Require custom integration work.
* Create maintenance overhead.
* Provide no unified management interface.

This fragmentation increases operational complexity and reduces reliability.

---

## Proposed Solution

ROCK SUITE provides:

* Shared API
* Shared data layer
* Shared permission framework
* Shared event system
* Shared administrative tools
* Shared web infrastructure

Modules become components of a single ecosystem rather than isolated products.

---

## Migration Consideration

Many server owners are already running existing solutions:

```text
LuckPerms     → rock-permissions replacement
GriefPrevention / Lands    → rock-claims replacement
Vault / EssentialsX        → rock-economy replacement
```

A migration toolset (RMG) is planned for the 1.0 release cycle. Import formats for the above solutions will be defined before the Release Candidate phase begins.

---

## Expected Benefits

### Operational Benefits

* Reduced setup time
* Reduced maintenance effort
* Consistent user experience
* Simplified upgrades
* Single place for all administration

### Technical Benefits

* Unified architecture
* Cross-loader portability
* Reduced integration costs
* Centralised security controls
* One database, one config, one permission model

### Community Benefits

* Easier extension development
* Predictable, stable APIs
* Better documentation
* Larger ecosystem potential

---

## Risks

### Scope Expansion

**Risk:**
Attempting to build too many modules simultaneously.

**Mitigation:**
Strict phased roadmap. Alpha contains no feature modules at all.

---

### Loader Complexity

**Risk:**
Maintaining compatibility across ecosystems.

**Mitigation:**
Strong abstraction boundaries. Loader adapters are the only code permitted to touch loader APIs.

---

### Performance

**Risk:**
Centralised systems becoming bottlenecks.

**Mitigation:**
Service-oriented architecture with async-first design. Caching layer between repositories and services.

---

### Adoption

**Risk:**
Entrenched competitors (LuckPerms, Towny, EssentialsX).

**Mitigation:**
Migration tooling. Focus on integration quality rather than feature quantity.

---

# Stakeholder Register

| Stakeholder            | Role                | Interest                        |
|------------------------|---------------------|---------------------------------|
| Founder                | Project Owner       | Strategic direction             |
| Core Developers        | Engineering         | Architecture and implementation |
| Server Owners          | Primary Users       | Stability and functionality     |
| Server Administrators  | Operational Users   | Management tools                |
| Mod Developers         | Integrators         | API access                      |
| Community Contributors | Contributors        | Ecosystem growth                |
| Hosting Providers      | Deployment Partners | Operational compatibility       |
| Players                | End Users           | Server experience               |

---

# Constraints

### Technical

* Must support Fabric and NeoForge (initial release).
* Modules must not directly depend on loader APIs.
* All cross-module communication through Rock API abstractions.
* Java 21 LTS required.
* Gradle (Kotlin DSL) required.
* Google Guice as DI container.
* JDBI3 + HikariCP for data access.

### Performance

* Minimal TPS impact (< 0.25 ms/tick average per module).
* Async operations whenever possible.
* Database operations isolated from game thread.

### Licensing

* Apache License 2.0.
* All contributors must sign the ROCK Developer Certificate of Origin before their first merged pull request.
* Must comply with Mojang and Microsoft platform policies.

---

# Core Design Principles

### Principle 1

Platform First, Features Second.

The platform that makes modules easy comes before any individual module.

---

### Principle 2

Loader Independence.

Modules must never import loader-specific classes. This is enforced by architecture, not trust.

---

### Principle 3

API Driven Architecture.

All integration occurs through `rock-api`. Internal implementation details are private.

---

### Principle 4

Single Source of Truth.

Every piece of data has one authoritative owner. Cross-module data sharing happens through services and events, not direct database access.

---

### Principle 5

Extensibility Before Complexity.

Every system is designed assuming third parties will extend it. The platform must be as easy to build on top of as Spring Boot.

---

### Engineering Principle Zero

> Build the platform that makes the modules easy.

Do not build modules and hope a platform emerges later.
