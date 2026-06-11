package dev.rock.api.domain.bounds;

import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Chunk-based claims for Phase 1: a claim is a set of claimed chunks within
 * one world. Lower storage cost, simple overlap detection, familiar Towny
 * model; block precision can be layered on later via {@link ClaimBounds}.
 */
public final class ChunkBounds implements ClaimBounds {

    /** Full world height range used for volume calculations (1.18+ build height). */
    private static final long CHUNK_VOLUME = 16L * 16L * 384L;

    private final UUID worldId;
    private final Set<ChunkCoordinate> chunks;

    public record ChunkCoordinate(int chunkX, int chunkZ) {

        public static ChunkCoordinate ofBlock(int blockX, int blockZ) {
            return new ChunkCoordinate(blockX >> 4, blockZ >> 4);
        }
    }

    public ChunkBounds(UUID worldId, Set<ChunkCoordinate> chunks) {
        this.worldId = Objects.requireNonNull(worldId, "worldId");
        this.chunks = Set.copyOf(chunks);
        if (this.chunks.isEmpty()) {
            throw new IllegalArgumentException("ChunkBounds requires at least one chunk");
        }
    }

    public Set<ChunkCoordinate> chunks() {
        return chunks;
    }

    @Override
    public UUID worldId() {
        return worldId;
    }

    @Override
    public BoundsType type() {
        return BoundsType.CHUNK_BASED;
    }

    @Override
    public boolean contains(int x, int y, int z) {
        return chunks.contains(ChunkCoordinate.ofBlock(x, z));
    }

    @Override
    public boolean overlaps(ClaimBounds other) {
        if (!worldId.equals(other.worldId())) {
            return false;
        }
        if (other instanceof ChunkBounds cb) {
            return chunks.stream().anyMatch(cb.chunks::contains);
        }
        // Fallback for foreign implementations: probe one block per chunk corner.
        return chunks.stream().anyMatch(c -> other.contains(c.chunkX() << 4, 0, c.chunkZ() << 4));
    }

    @Override
    public long volume() {
        return chunks.size() * CHUNK_VOLUME;
    }

    /** Serialises as "chunkX,chunkZ;chunkX,chunkZ;...". */
    public String serializeChunks() {
        return chunks.stream()
                .map(c -> c.chunkX() + "," + c.chunkZ())
                .sorted()
                .collect(Collectors.joining(";"));
    }

    public static ChunkBounds deserializeChunks(UUID worldId, String value) {
        Set<ChunkCoordinate> parsed = Set.of(value.split(";")).stream()
                .map(s -> {
                    String[] parts = s.split(",");
                    return new ChunkCoordinate(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
                })
                .collect(Collectors.toUnmodifiableSet());
        return new ChunkBounds(worldId, parsed);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ChunkBounds cb && worldId.equals(cb.worldId) && chunks.equals(cb.chunks);
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldId, chunks);
    }

    @Override
    public String toString() {
        return "ChunkBounds[world=" + worldId + ", chunks=" + chunks.size() + "]";
    }
}
