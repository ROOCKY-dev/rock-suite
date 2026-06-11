# ROCK Technical Requirements Specification (TRS)

**Version:** 2.0
**Status:** Approved Foundation Document

---

# 1. Purpose

This document defines measurable engineering requirements for all ROCK platform components.

These requirements apply to:

* Rock Core
* Rock API
* Rock Data
* All official modules
* Third-party modules seeking ROCK certification

---

# 2. Supported Platforms

## Tier 1 — Required for Initial Release

```text
Minecraft 1.21+
Fabric
NeoForge
```

These are the two actively maintained modern loaders. NeoForge is the active fork of legacy Forge as of 2023.

---

## Tier 2 — Phase 2 Targets (best-effort)

```text
Legacy Forge    ← maintained for compatibility; not a primary target
Paper
Velocity
```

**Note on Forge vs NeoForge:** These are now separate, diverging projects. Maintaining both in Tier 1 would double Loader Adapter maintenance burden. Legacy Forge support is provided where feasible but is not a blocking requirement for any ROCK release.

---

## Tier 3 — Future Roadmap

```text
Folia           ← requires special async-safe design work
```

---

## Requirement

Loader-specific code SHALL NOT exist outside Loader Adapters.

This requirement applies equally to Tier 1 and Tier 2 loaders.

---

# 3. Performance Requirements

## TPS Impact

Target:

```text
Average impact: < 0.25 ms/tick
```

per installed ROCK module under normal operation.

Maximum:

```text
< 1.0 ms/tick
```

under peak load.

---

## Performance Measurement Methodology

All TPS claims must be validated using:

```text
Profiler:    Spark (https://spark.lucko.me)
Hardware:    4-core CPU, 8 GB RAM allocated to JVM
Player load: 50 simulated concurrent players (using MockBukkit or equivalent)
Duration:    10-minute sustained test run
Metric:      95th percentile tick duration during steady state
```

Performance claims without these conditions specified are not accepted.

---

## Tick Thread Protection

The Minecraft server thread MUST NEVER:

* Perform database operations
* Perform HTTP requests
* Perform Discord API requests
* Perform file scanning operations

These operations SHALL be asynchronous.

---

## Event Processing

Target:

```text
< 100 microseconds
```

per event listener under normal load.

---

# 4. Memory Requirements

## Core

Target:

```text
< 50 MB RAM
```

for Rock Core at steady state.

---

## Modules

Target:

```text
< 25 MB
```

per typical module at steady state.

---

## Scaling Goal

Support:

```text
500+
concurrent players
```

without unbounded memory growth.

---

# 5. Database Requirements

## Supported Backends

### Tier 1

* SQLite (default — zero-config, local deployment)
* PostgreSQL (recommended for production)

---

### Tier 2

* MariaDB

---

## Connection Pooling

Database connections SHALL be managed through a connection pool.

**Required implementation: HikariCP.**

Pool size must be configurable per deployment.

Recommended defaults:

```toml
[database.pool]
maximum-pool-size = 10
minimum-idle = 2
connection-timeout = 30000   # ms
idle-timeout = 600000        # ms
max-lifetime = 1800000       # ms
```

Direct `DriverManager.getConnection()` calls are forbidden in all platform and module code.

---

## Database Independence

Modules SHALL communicate through:

```java
DataService
```

only.

Direct SQL access from module code is prohibited.

---

## Migration System

All schema changes SHALL be versioned using Flyway.

Example naming convention:

```text
V001__create_players.sql
V002__create_claims.sql
V003__add_transactions.sql
```

---

## Rollback Support

Every migration must define both `UP` and `DOWN` procedures.

DOWN migrations must be transactional where the database engine supports it.

If a DOWN migration fails mid-execution, the failure must be logged and the schema must not be left in a partially downgraded state.

---

# 6. API Stability

## Semantic Versioning

Required:

```text
MAJOR.MINOR.PATCH
```

---

## Compatibility Guarantees

PATCH:

```text
No breaking changes. Bug fixes only.
```

MINOR:

```text
Backward-compatible additions only.
New methods, new events, new optional parameters.
```

MAJOR:

```text
Breaking changes allowed.
Must be preceded by a deprecation cycle.
```

---

# 7. Public API Policy

Anything inside:

```text
rock-api
```

is public contract.

Anything else:

```text
internal packages
```

may change at any time without notice.

Internal APIs must be annotated:

```java
@RockInternal
```

Third-party code using `@RockInternal` APIs has no stability guarantee.

---

# 8. Event Bus Requirements

## Event Ordering

Events SHALL execute across five priority levels:

```text
FIRST
EARLY
NORMAL
LATE
LAST
```

Default listener priority is `NORMAL`.

Listeners at `FIRST` receive the event before any other listeners.

Listeners at `LAST` see all mutations made by earlier listeners.

---

## Event Cancellation

Supported for player-action events where intervention is meaningful:

```text
ClaimCreateEvent
PlayerBanEvent
TransactionEvent
```

A cancelled event at `FIRST` priority will not be seen by `NORMAL` listeners unless specifically configured.

---

## Async Events

Supported for:

```text
Database operations
Web requests
Discord API calls
```

Async events must never modify Minecraft world state directly.

---

# 9. Security Requirements

## Authentication

All web services require:

```text
JWT Authentication
```

Tokens must expire. Refresh tokens must be supported.

---

## Password Storage

Passwords SHALL NEVER be stored in plaintext.

Required algorithm:

```text
Argon2id
```

with appropriate memory and iteration parameters.

---

## Audit Logging

Administrative actions MUST produce `RockAuditEntry` records.

Examples:

```text
Permission changes
Claim deletion
Balance changes
Ban actions
Config reloads
Module enable / disable
```

---

# 10. Logging Requirements

## Structured Logging

Required format:

```json
{
  "timestamp": "ISO-8601",
  "module": "rock.claims",
  "level": "INFO",
  "message": "Claim created"
}
```

---

## Log Levels

```text
TRACE
DEBUG
INFO
WARN
ERROR
FATAL
```

Production deployments default to `INFO`. `DEBUG` and `TRACE` are available via config.

---

## Retention

Configurable per deployment.

Default:

```text
90 days
```

---

# 11. Configuration Requirements

## Format

Primary:

```text
TOML
```

Secondary (optional adapter):

```text
YAML
```

---

## Secrets Management

Configuration files contain non-sensitive settings.

Secrets (database passwords, Discord tokens, API keys) SHALL be injected via:

1. **Environment variables** — primary mechanism:
   ```toml
   password = "${env.ROCK_DB_PASSWORD}"
   ```

2. **Secrets file** — `.secrets.env` excluded from version control:
   ```text
   ROCK_DB_PASSWORD=changeme
   ROCK_DISCORD_TOKEN=Bot xxxxx
   ```

Platform documentation MUST warn against committing secrets to version control.

---

## Live Reload

Supported for:

```text
Permissions
Economy Rates
Discord Settings
```

Core platform settings require a full restart.

---

# 12. Web Platform Requirements

The web dashboard is ROCK's flagship public-facing component.

---

## Dashboard

Must provide:

```text
Player Management
Claims Management
Economy Management
Audit Logs
Module Configuration
User Accounts (web login)
```

---

## Responsiveness

Target:

```text
< 250 ms
```

for typical dashboard requests (read operations).

---

## Concurrent Dashboard Users

Target:

```text
100+ simultaneous users
```

without degrading game server performance.

---

## API Versioning

REST API must be versioned from day one:

```text
/api/v1/players
/api/v1/claims
```

---

# 13. Discord Integration Requirements

## Connection Stability

Automatic reconnect on disconnect.

Exponential backoff with a maximum retry interval of 60 seconds.

---

## Rate Limit Compliance

Discord API rate limits must be respected.

Message send operations must use a rate-limited queue.

---

## Message Queue

Required.

Discord messages must never be sent directly from the Minecraft game thread.

---

# 14. Backup Requirements

## Automated Backups

Built-in backup scheduling supported.

---

## Restore Support

Required. Restore must be possible without manual SQL.

---

## Export Formats

Minimum:

```text
JSON
CSV
```

---

# 15. Scalability Requirements

## Small Server

```text
1–20 Players
```

Fully supported. SQLite is acceptable.

---

## Medium Server

```text
20–150 Players
```

Primary design target. PostgreSQL recommended.

---

## Large Server

```text
150–500 Players
```

Supported.

---

## Enterprise Goal

```text
500–2000 Players
```

Long-term objective. May require Redis caching layer.

---

# 16. Plugin / Module Ecosystem

## Third-Party Modules

Must be a first-class experience.

---

## SDK / MDK

Provide:

```text
Project Templates (Maven archetype / GitHub template)
Testing Framework (MockROCK or equivalent)
Documentation
Example modules
```

---

## Artifact Publishing

ROCK platform artifacts are published to:

```text
Maven Central   ← stable releases, group: dev.rock
GitHub Packages ← pre-release / snapshot builds
```

Third-party module authors add the following to their build:

```kotlin
// build.gradle.kts
dependencies {
    compileOnly("dev.rock:rock-api:1.0.0")
}
```

---

# 17. Reliability Targets

## Startup Success Rate

Target:

```text
99.9%
```

successful initialisation under normal conditions.

---

## Data Integrity

Target:

```text
Zero silent data corruption
```

All database writes are transactional. All failures are logged.

---

## Module Crash Isolation

If a single module crashes:

```text
Module is disabled
All other modules continue running
Server continues running
Crash is logged with full stack trace
Admin is notified via configured channel
```

The server must not crash because a module crashed.

---

# 18. Development Requirements

## Java Version

Required:

```text
Java 21 LTS
```

Record classes, sealed interfaces, pattern matching, and virtual threads (Project Loom) are available and encouraged.

---

## Build System

Required:

```text
Gradle (Kotlin DSL)
```

Groovy DSL is not permitted for new modules.

---

## Testing Framework

Required:

```text
Test runner:     JUnit 5 (Jupiter)
Mocking:         Mockito 5.x
Integration:     Testcontainers (for database integration tests)
Coverage:        JaCoCo
```

All four are mandatory in CI.

---

## Coverage Targets

| Component | Required Coverage |
|-----------|------------------|
| rock-core, rock-api, rock-data | 80% |
| Official modules | 70% |
| Integration tests (line coverage) | 60% |
| Third-party certification minimum | 70% |

---

# 19. Backward Compatibility Policy

ROCK must become infrastructure.

Infrastructure must be predictable.

---

Supported API lifetime:

```text
Minimum 24 calendar months
```

after deprecation notice, regardless of version count.

Deprecated APIs must emit log warnings at `WARN` level when invoked.

---

# 20. Non-Goals (Important)

ROCK SHALL NOT become:

* A gameplay overhaul framework
* A content modding API replacement
* A world generation framework
* A mod loader replacement
* A game engine

ROCK exists to manage servers and server communities.

---

# 21. Definition of Success

A server owner should be able to install:

```text
Rock Core
Rock Claims
Rock Permissions
Rock Economy
Rock Discord
Rock Web
```

and receive a fully integrated management platform without needing a collection of unrelated mods, bridge mods, synchronisation plugins, or custom scripts.

---

# 22. Data Privacy Requirements

## Player Data Classification

The following are classified as personal data under GDPR and equivalent regulations:

```text
UUID (when linked to a real person)
Username
IP Address
Join timestamps
Chat history
```

---

## Right to Erasure

Platform MUST support player data deletion on request.

Deletion procedure:

1. Replace identifying fields with anonymised placeholders
2. Emit `RockPlayerDeleted` event for modules to clean up their data
3. Set `PlayerStatus.DELETED` on the `RockPlayer` record
4. Retain anonymised `RockAuditEntry` records (required for server integrity)
5. Log the erasure request as its own audit entry

```java
// Example post-erasure player record
UUID:      original UUID retained (needed for foreign keys)
Username:  "deleted-user"
Status:    DELETED
DeletedAt: <timestamp>
```

---

## Data Minimisation

Platform must not collect data it does not need.

Modules collecting additional personal data must document it in their module manifest.

---

## Configuration

Servers in jurisdictions requiring GDPR compliance should configure:

```toml
[privacy]
enable-erasure-support = true
data-retention-days = 365    # log/audit retention
```

---

# 23. Strategic Observation

Most Minecraft projects start with code and gradually invent standards.

ROCK is doing the opposite:

```text
1. Vision
2. Governance
3. Architecture
4. Domain Model
5. Technical Requirements
6. Engineering Standards
7. Implementation
```

That sequence dramatically increases the probability that five years from now ROCK still feels like one coherent platform rather than ten loosely related mods.
