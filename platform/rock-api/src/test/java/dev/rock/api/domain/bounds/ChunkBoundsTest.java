package dev.rock.api.domain.bounds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rock.api.domain.bounds.ChunkBounds.ChunkCoordinate;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChunkBoundsTest {

    private final UUID world = UUID.randomUUID();

    @Test
    void containsMatchesAnyBlockInsideClaimedChunks() {
        ChunkBounds bounds = new ChunkBounds(world, Set.of(new ChunkCoordinate(0, 0), new ChunkCoordinate(1, 0)));

        assertTrue(bounds.contains(0, 64, 0));
        assertTrue(bounds.contains(15, -60, 15));
        assertTrue(bounds.contains(16, 200, 0)); // chunk (1,0)
        assertFalse(bounds.contains(32, 64, 0)); // chunk (2,0)
        assertFalse(bounds.contains(-1, 64, 0)); // chunk (-1,0)
    }

    @Test
    void negativeBlockCoordinatesMapToNegativeChunks() {
        assertEquals(new ChunkCoordinate(-1, -1), ChunkCoordinate.ofBlock(-1, -16));
    }

    @Test
    void overlapsRequiresSameWorldAndSharedChunk() {
        ChunkBounds a = new ChunkBounds(world, Set.of(new ChunkCoordinate(3, 3)));
        ChunkBounds sameChunk = new ChunkBounds(world, Set.of(new ChunkCoordinate(3, 3)));
        ChunkBounds neighbour = new ChunkBounds(world, Set.of(new ChunkCoordinate(4, 3)));
        ChunkBounds otherWorld = new ChunkBounds(UUID.randomUUID(), Set.of(new ChunkCoordinate(3, 3)));

        assertTrue(a.overlaps(sameChunk));
        assertFalse(a.overlaps(neighbour));
        assertFalse(a.overlaps(otherWorld));
    }

    @Test
    void chunkSerializationRoundTrips() {
        ChunkBounds bounds = new ChunkBounds(world, Set.of(new ChunkCoordinate(-2, 7), new ChunkCoordinate(0, 0)));

        ChunkBounds parsed = ChunkBounds.deserializeChunks(world, bounds.serializeChunks());

        assertEquals(bounds, parsed);
    }

    @Test
    void rejectsEmptyChunkSet() {
        assertThrows(IllegalArgumentException.class, () -> new ChunkBounds(world, Set.of()));
    }
}
