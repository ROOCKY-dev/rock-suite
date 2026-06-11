package dev.rock.api.services;

import dev.rock.api.events.world.BlockChangeType;
import java.time.Instant;
import java.util.UUID;

/**
 * Filter for world-log lookups and rollbacks (CoreProtect-style grammar:
 * time window / actor / radius / action). Null fields mean "any".
 *
 * @param radius requires centerX/Y/Z when set; cubic radius in blocks
 */
public record LogQuery(
        UUID worldId,
        UUID actor,
        Instant since,
        Instant until,
        Integer centerX,
        Integer centerY,
        Integer centerZ,
        Integer radius,
        BlockChangeType action,
        int limit) {

    public LogQuery {
        if (radius != null && (centerX == null || centerY == null || centerZ == null)) {
            throw new IllegalArgumentException("radius requires centerX/centerY/centerZ");
        }
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder — most call sites set only one or two filters. */
    public static final class Builder {
        private UUID worldId;
        private UUID actor;
        private Instant since;
        private Instant until;
        private Integer centerX;
        private Integer centerY;
        private Integer centerZ;
        private Integer radius;
        private BlockChangeType action;
        private int limit = 1000;

        public Builder world(UUID worldId) {
            this.worldId = worldId;
            return this;
        }

        public Builder actor(UUID actor) {
            this.actor = actor;
            return this;
        }

        public Builder since(Instant since) {
            this.since = since;
            return this;
        }

        public Builder until(Instant until) {
            this.until = until;
            return this;
        }

        public Builder around(int x, int y, int z, int radius) {
            this.centerX = x;
            this.centerY = y;
            this.centerZ = z;
            this.radius = radius;
            return this;
        }

        public Builder action(BlockChangeType action) {
            this.action = action;
            return this;
        }

        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }

        public LogQuery build() {
            return new LogQuery(worldId, actor, since, until, centerX, centerY, centerZ, radius, action, limit);
        }
    }
}
