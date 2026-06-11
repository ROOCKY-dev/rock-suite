# ROCK Domain Model Specification (DMS)

**Version:** 2.0
**Status:** Approved Foundation Document

## Purpose

Define the canonical objects that exist within the ROCK ecosystem.

These objects are:

* Loader-independent
* Database-independent
* Module-independent

Every API, event, database table, web endpoint, and service will be built around these definitions.

---

# Domain Hierarchy

```text
RockObject
│
├── RockPlayer
├── RockServer
├── RockWorld
├── RockClaim
│   └── ClaimBounds
├── RockGroup
├── RockPermission
├── RockEconomyAccount
├── RockTransaction
├── RockAuditEntry
├── RockDiscordLink
├── RockPunishment
├── RockMetadata
└── OwnerReference (sealed interface)
```

---

# Core Entity Rules

### Rule 1

Every entity has a globally unique identifier.

```java
UUID id
```

Never rely on usernames.

Never rely on claim names.

Never rely on Discord IDs.

---

### Rule 2

Entities must survive renames.

A player changing their name must not affect any ownership records.

A claim being renamed must not break references to it.

---

### Rule 3

Entities must be serialisable.

All entities must support:

```java
JSON
Database
Network
Cache
```

representation.

---

### Rule 4 — Soft Delete

Domain entities are never hard-deleted.

Instead:

```java
Instant deletedAt   // null if active; non-null if deleted
```

Hard deletion is reserved for administrative data purge operations only.

**Why:**

Audit history requires knowing what existed before.

A deleted claim must still be queryable for grievance resolution.

A deleted economy account must preserve transaction history.

---

# RockPlayer

## Purpose

Represents a human player.

Canonical identity object for the entire platform.

---

## Properties

```java
UUID id
String username
Locale preferredLocale     // added: supports localisation framework
Instant firstJoin
Instant lastSeen
PlayerStatus status
Instant deletedAt          // null if active; supports right-to-erasure workflows
```

---

## Player Status Values

```text
ACTIVE
BANNED
SUSPENDED
DELETED       // GDPR erasure — player data anonymised
```

---

## Relationships

```text
RockPlayer
│
├── Groups
├── Permissions
├── Claims
├── Economy Accounts
├── Punishments
├── Discord Link
└── Metadata
```

---

## Important Decision

Player data belongs to the platform.

Not to Claims.

Not to Economy.

Not to Permissions.

Everyone references the same `RockPlayer`.

---

# RockServer

## Purpose

Represents the running Minecraft server.

---

## Properties

```java
UUID id
String name
String version
ServerType type
```

---

## Server Type Values

```text
FABRIC
NEOFORGE
FORGE
PAPER
VELOCITY
FOLIA
UNKNOWN
```

---

# RockWorld

## Purpose

Represents a world or dimension.

---

## Properties

```java
UUID id
String name
WorldType type
```

---

## World Type Values

```text
OVERWORLD
NETHER
END
CUSTOM
```

---

# RockClaim

This is the flagship domain object.

---

## Purpose

Represents an owned region.

---

## Properties

```java
UUID id
String displayName

OwnerReference owner       // see OwnerReference below — not a raw UUID

ClaimType type

ClaimBounds bounds         // physical region definition (see ClaimBounds below)

Instant created
Instant modified
Instant deletedAt          // null if active
```

---

## Relationships

```text
RockClaim
│
├── Owner
├── Members
├── Permissions
├── Balance
├── Tax Rules
├── Metadata
└── Audit History
```

---

## Claim Types

```text
PLAYER
TOWN
FACTION
NATION
ADMIN
SYSTEM
```

---

# ClaimBounds

## Purpose

Defines the physical region of a claim.

Decoupled from `RockClaim` to allow different spatial implementations without changing the claim model.

---

## Interface

```java
public interface ClaimBounds {

    UUID worldId();

    BoundsType type();

    boolean contains(int x, int y, int z);

    boolean overlaps(ClaimBounds other);

    long volume();

}
```

---

## Bounds Types

```text
CHUNK_BASED      // initial implementation — whole chunks only
BLOCK_CUBOID     // future — exact block-level precision
POLYGON          // future — arbitrary shape
```

---

## Initial Implementation: ChunkBounds

```java
// Chunk-based claims for Phase 1
// A claim is a set of claimed chunks within one world
public final class ChunkBounds implements ClaimBounds {

    private final UUID worldId;
    private final Set<ChunkCoordinate> chunks;

    // chunk coordinate record
    public record ChunkCoordinate(int chunkX, int chunkZ) {}

}
```

**Why chunk-based first:**

* Lower storage cost (one row per chunk, not per block)
* Simpler overlap detection
* Familiar to server owners (Towny model)
* Block-precision can be layered on later using the same interface

---

# OwnerReference

## Purpose

Abstracts ownership so claims, economy accounts, and other ownable entities can be owned by players, groups, claims, or the system — without the data model assuming player ownership.

---

## Definition

```java
public sealed interface OwnerReference
        permits PlayerOwner, GroupOwner, ClaimOwner, SystemOwner {

    UUID id();

    OwnerType type();

    // Compact DB-safe serialisation format: "PLAYER:uuid" / "GROUP:uuid" etc.
    String serialize();

    static OwnerReference deserialize(String value) {
        // parses "TYPE:uuid" format
    }
}

public record PlayerOwner(UUID id) implements OwnerReference {
    public OwnerType type() { return OwnerType.PLAYER; }
    public String serialize() { return "PLAYER:" + id; }
}

public record GroupOwner(UUID id) implements OwnerReference {
    public OwnerType type() { return OwnerType.GROUP; }
    public String serialize() { return "GROUP:" + id; }
}

public record ClaimOwner(UUID id) implements OwnerReference {
    public OwnerType type() { return OwnerType.CLAIM; }
    public String serialize() { return "CLAIM:" + id; }
}

public record SystemOwner(UUID id) implements OwnerReference {
    public OwnerType type() { return OwnerType.SYSTEM; }
    public String serialize() { return "SYSTEM:" + id; }
}
```

---

## DB Storage

`OwnerReference` is stored as a single VARCHAR column:

```text
"PLAYER:a1b2c3d4-..."
"GROUP:e5f6a7b8-..."
"SYSTEM:00000000-..."
```

One column. No join needed to identify owner type.

---

## Why This Matters

Using `UUID ownerPlayer` everywhere would require a schema migration the moment a Town or Faction needs to own a claim. `OwnerReference` absorbs that future requirement now.

---

# RockGroup

(permissions / ranks)

---

## Purpose

Represents a rank or role.

---

## Properties

```java
UUID id
String name
int priority
Instant deletedAt          // soft-delete
```

---

## Priority Rules

Lower number means higher priority.

Example:
```text
Owner      → priority 0  (highest)
Admin      → priority 10
Moderator  → priority 20
VIP        → priority 50
Member     → priority 100
Default    → priority 999 (lowest)
```

**Tie-breaking rule:** If two groups share the same priority value, they are resolved alphabetically by `name` ascending. Duplicate priority values are a validation warning on group creation. Two groups at the same priority level with conflicting permissions produce a log warning; the alphabetically earlier group wins.

---

## Examples

```text
Default
Member
VIP
Moderator
Admin
Owner
```

---

# RockPermission

---

## Purpose

Represents a permission node.

---

## Properties

```java
String node
PermissionState state
```

---

## Permission State Values

```text
ALLOW
DENY
UNSET
```

---

## Examples

```text
rock.claims.create
rock.admin.reload
rock.economy.withdraw
```

---

# RockEconomyAccount

---

## Purpose

Represents an account capable of holding value.

---

## Design Choice

Accounts are not limited to players.

---

## Account Owner Types

```text
PLAYER
CLAIM
FACTION
SERVER
SYSTEM
```

---

## Examples

```text
Player Wallet
Town Treasury
Faction Bank
Server Treasury
```

---

## Properties

```java
UUID id
OwnerReference owner       // not UUID — uses OwnerReference
AccountType type
BigDecimal balance
Instant deletedAt
```

---

# RockTransaction

---

## Purpose

Represents a value transfer between accounts.

---

## Properties

```java
UUID id

OwnerReference sourceAccount

OwnerReference targetAccount

BigDecimal amount

TransactionStatus status   // added: required for economy integrity

UUID reversalOf            // nullable — links to original if this is a reversal

Instant timestamp

String reason
```

---

## Transaction Status Values

```text
PENDING       // initiated but not yet committed
COMPLETED     // successfully applied to both accounts
FAILED        // attempted but did not complete (e.g. insufficient funds)
REVERSED      // completed but subsequently reversed; reversalOf is non-null
```

---

## Examples

```text
Player pays tax         → COMPLETED
Town buys chunk         → COMPLETED / FAILED
Admin grant             → COMPLETED
Auction purchase        → PENDING → COMPLETED
Refund                  → COMPLETED + reversalOf pointing to original
```

---

# RockAuditEntry

This becomes critical at scale.

---

## Purpose

Immutable history record.

Audit entries are never updated. Never deleted except by explicit administrative data purge.

---

## Properties

```java
UUID id

Instant timestamp

OwnerReference actor       // who performed the action

AuditAction action

String targetType          // what kind of entity was affected
UUID targetId

String details             // JSON-serialised action context
```

---

## Examples

```text
Claim Created
Money Withdrawn
Rank Assigned
Ban Applied
Permission Changed
Config Reloaded
```

---

# RockDiscordLink

---

## Purpose

Connects Minecraft and Discord identities.

---

## Properties

```java
UUID playerId

String discordId

Instant linkedAt

Instant unlinkedAt         // null if currently linked
```

No Discord logic belongs inside `RockPlayer`.

---

# RockPunishment

One unified system for all punishment types.

---

## Properties

```java
UUID id

PunishmentType type

UUID target                // player being punished

OwnerReference issuer      // can be a player, admin, or system

String reason

Instant created

Instant expires            // null = permanent

Instant revokedAt          // null if active; non-null if manually revoked

UUID revokedBy             // nullable — who revoked it
```

---

## Types

```text
BAN
MUTE
WARN
KICK
BLACKLIST
```

---

# RockMetadata

## Purpose

Extensible key-value storage attached to any domain entity.

Allows modules to store custom data against entities without requiring schema migrations.

---

## Properties

```java
UUID entityId              // the entity this metadata belongs to
String entityType          // "PLAYER", "CLAIM", "GROUP", etc.
String namespace           // owning module: "rock.discord", "rock.economy"
String key                 // the metadata key
String value               // JSON-serialised value
Instant lastModified
```

---

## Usage

```java
entity.setMetadata(
    "rock.discord",
    "linked.channel",
    "123456789"
);

entity.setMetadata(
    "rock.claims",
    "tax.rate",
    "5.0"
);
```

---

## Benefits

* No schema migration required for new per-entity data
* Modules store their own data without polluting core tables
* Queried by namespace to avoid key collisions

---

# Domain Events

Every domain object produces events.

---

## Examples

```text
RockPlayerCreated
RockPlayerJoined
RockPlayerDeleted           // GDPR erasure event

RockClaimCreated
RockClaimDeleted
RockClaimTransferred
RockClaimBoundsChanged

RockTransactionCreated
RockTransactionFailed
RockTransactionReversed

RockPunishmentApplied
RockPunishmentRevoked

RockPermissionGranted
RockPermissionRevoked

RockGroupCreated
RockGroupDeleted
```

These become the language of the entire platform.

---

# Architectural Observation

Most Minecraft management suites are built around **features**.

ROCK is built around **domains**.

That distinction sounds subtle but changes everything.

Bad architecture:

```text
Claims Module
Economy Module
Discord Module
```

Good architecture:

```text
Player Domain
Ownership Domain
Economy Domain
Permission Domain
Communication Domain
Moderation Domain
```

Modules become implementations of domain concepts.

That makes the platform dramatically easier to extend for the next decade.
