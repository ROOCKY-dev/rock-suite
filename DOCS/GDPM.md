# ROCK Governance & Development Process Manual (GDPM)

**Status:** Foundation Document
**Applies To:** All Official ROCK Components

---

# 1. Purpose

This document defines:

* Decision-making authority
* Development standards
* Contribution workflow
* Release governance
* API governance
* Module certification

The goal is to maintain architectural consistency throughout the lifetime of the project.

---

# 2. Governance Philosophy

ROCK follows:

## Centralized Architecture

Combined with

## Distributed Development

Meaning:

Anyone may contribute.

Not everyone may redefine architecture.

---

# 3. Governance Structure

```text
Project Owner
        │
Architecture Council
        │
Core Maintainers
        │
Module Maintainers
        │
Contributors
```

---

# 4. Project Owner

Initially:

Ahmed

---

## Responsibilities

* Define strategic vision
* Approve major roadmap changes
* Appoint Architecture Council members
* Resolve deadlocks

---

## Restrictions

Project Owner may not bypass technical review procedures.

Even founders must follow process.

---

# 5. Architecture Council

The most important group.

---

## Responsibilities

Own:

* Rock API
* Domain Model
* Core Architecture
* Security Model
* Compatibility Policy

---

## Approval Required For

### New Core Services

Example:

```text
NotificationService
IdentityService
MarketplaceService
```

---

### Public API Changes

Example:

```text
ClaimService
EconomyService
PermissionService
```

---

### Breaking Changes

Always require review.

---

# 6. Core Maintainers

Responsible for:

```text
Rock Core
Rock API
Rock Data
Rock Security
```

---

## Powers

May approve:

* Bug fixes
* Optimizations
* Documentation
* Internal refactoring

---

## Restrictions

Cannot introduce breaking API changes without Council approval.

---

# 7. Module Maintainers

Responsible for individual modules.

Examples:

```text
Rock Claims
Rock Economy
Rock Discord
Rock Web
```

---

## Responsibilities

* Roadmaps
* Releases
* Documentation
* Testing

---

# 8. Contributors

Anyone submitting:

* Code
* Documentation
* Tests
* Localization

---

Contributors have no architectural authority by default.

Authority is earned through participation.

---

# 9. Decision Framework

Not every decision requires a meeting.

---

## Type A

Minor

Examples:

```text
Bug Fix
Performance Improvement
Documentation Update
```

Approval:

Maintainer only.

---

## Type B

Significant

Examples:

```text
New Feature
New Service
New Module
```

Approval:

Maintainer + Review

---

## Type C

Architectural

Examples:

```text
API Change
Domain Model Change
Event System Change
```

Approval:

Architecture Council

---

## Type D

Strategic

Examples:

```text
Loader Expansion
Licensing Change
Governance Change
```

Approval:

Project Owner + Council

---

# 10. RFC Process

Large changes require RFCs.

---

RFC = Request For Comments

---

## Required For

* New Core Systems
* New Domains
* Breaking Changes
* Major Integrations

---

## RFC Template

```text
Title

Problem

Proposed Solution

Alternatives Considered

Compatibility Impact

Migration Plan

Risks

Recommendation
```

---

# 11. API Governance

Public APIs are sacred.

---

## Rule

Public APIs cannot be changed casually.

---

### Allowed

```java
new methods
new events
new optional features
```

---

### Forbidden

```java
changing signatures
removing methods
changing semantics
```

without deprecation cycle.

---

# 12. Deprecation Policy

Minimum:

```text
2 major releases
```

before removal.

---

Example

```text
v2.0 deprecated

v3.0 warning

v4.0 removal
```

---

# 13. Official Module Certification

Not every module becomes an official ROCK module.

---

Requirements:

### Architecture Compliance

Must use:

* Rock API
* Rock Events
* Rock Services

---

### Testing

Minimum coverage:

```text
70%
```

---

### Documentation

Required.

---

### Security Review

Required for:

* Web
* Network
* Authentication

modules.

---

# 14. Release Strategy

## Core Releases

```text
Major
Minor
Patch
```

---

Example:

```text
3.2.5
```

---

## Schedule

### Major

Every:

```text
12-18 months
```

---

### Minor

Every:

```text
2-3 months
```

---

### Patch

As needed.

---

# 15. Branching Model

```text
main

develop

release/*
hotfix/*
feature/*
```

---

No direct commits to main.

---

# 16. Code Review Requirements

Every pull request requires:

### 1 Review

Normal modules.

---

### 2 Reviews

Core systems.

---

### 3 Reviews

Security-sensitive systems.

---

# 17. Security Governance

Security reports handled privately.

---

Never disclose:

* Active exploits
* Authentication vulnerabilities
* Data corruption issues

before patch availability.

---

# 18. Documentation Requirements

Every official module must include:

## User Documentation

For server owners.

---

## Administrator Documentation

For operators.

---

## Developer Documentation

For extension authors.

---

## API Documentation

Generated automatically.

---

# 19. Community Governance

Community participation encouraged.

---

Community may:

* Propose RFCs
* Report issues
* Submit code
* Vote in surveys

---

Community does not directly control architecture.

---

# 20. Project Values

## Consistency

Prefer one standard solution.

---

## Stability

Avoid unnecessary breaking changes.

---

## Transparency

Decisions documented publicly.

---

## Extensibility

Design for future modules.

---

## Performance

Protect server performance first.

---

# 21. Architecture Protection Rules

The following require formal approval:

### Domain Model Changes

Examples:

```text
RockPlayer
RockClaim
RockTransaction
```

---

### Service Changes

Examples:

```text
ClaimService
EconomyService
PermissionService
```

---

### Event Model Changes

Examples:

```text
ClaimCreatedEvent
BalanceChangedEvent
```

---

### Loader Abstraction Changes

Examples:

```text
Fabric Adapter
NeoForge Adapter
```

---

# 22. Constitutional Principle

The most important governance rule:

> Convenience must never override architecture.

If a contributor proposes:

```text
"Let's just access Fabric directly."
```

or

```text
"Let's bypass the event system."
```

the default answer is:

**No.**

The architecture exists to protect the platform's future.
