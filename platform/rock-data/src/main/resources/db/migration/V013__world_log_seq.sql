-- Deterministic ordering for same-millisecond world-log entries: a monotonic
-- sequence assigned by the writer, used as the ORDER BY tiebreaker. Without it
-- rollback's newest-first ordering is arbitrary within one tick.
ALTER TABLE rock_world_log ADD COLUMN seq BIGINT NOT NULL DEFAULT 0;
