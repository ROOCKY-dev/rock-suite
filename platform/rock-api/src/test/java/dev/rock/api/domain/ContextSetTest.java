package dev.rock.api.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ContextSetTest {

    @Test
    void emptyContextIsGlobalAndAlwaysApplies() {
        assertTrue(ContextSet.empty().satisfiedBy(ContextSet.empty()));
        assertTrue(ContextSet.empty().satisfiedBy(ContextSet.of("world", "overworld")));
        assertEquals(0, ContextSet.empty().specificity());
        assertEquals("", ContextSet.empty().serialize());
    }

    @Test
    void subsetSemantics() {
        ContextSet worldOnly = ContextSet.of("world", "overworld");
        ContextSet query = ContextSet.of("world", "overworld", "gamemode", "survival");

        assertTrue(worldOnly.satisfiedBy(query), "node context ⊆ query context applies");
        assertFalse(query.satisfiedBy(worldOnly), "more demanding context does not apply");
        assertFalse(ContextSet.of("world", "nether").satisfiedBy(query), "value mismatch");
    }

    @Test
    void serializationIsCanonicalAndRoundTrips() {
        ContextSet set = ContextSet.of("world", "overworld", "dimension", "minecraft:overworld");

        String serialized = set.serialize();

        assertEquals("dimension=minecraft:overworld;world=overworld", serialized, "keys sorted");
        assertEquals(set, ContextSet.deserialize(serialized));
        assertEquals(ContextSet.empty(), ContextSet.deserialize(""));
        assertEquals(ContextSet.empty(), ContextSet.deserialize(null));
    }

    @Test
    void rejectsMalformedPairs() {
        assertThrows(IllegalArgumentException.class, () -> ContextSet.deserialize("no-equals-sign"));
    }
}
