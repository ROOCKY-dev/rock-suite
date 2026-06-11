package dev.rock.api.domain.owner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class OwnerReferenceTest {

    @ParameterizedTest
    @EnumSource(OwnerType.class)
    void serializeRoundTripsForEveryOwnerType(OwnerType type) {
        UUID id = UUID.randomUUID();
        OwnerReference ref = OwnerReference.of(type, id);

        OwnerReference parsed = OwnerReference.deserialize(ref.serialize());

        assertEquals(ref, parsed);
        assertEquals(type, parsed.type());
        assertEquals(id, parsed.id());
    }

    @Test
    void serializeUsesCompactDbFormat() {
        UUID id = UUID.fromString("a1b2c3d4-0000-0000-0000-000000000001");
        assertEquals("PLAYER:" + id, new PlayerOwner(id).serialize());
    }

    @Test
    void deserializeRejectsGarbage() {
        assertThrows(IllegalArgumentException.class, () -> OwnerReference.deserialize("not-a-ref"));
        assertThrows(IllegalArgumentException.class, () -> OwnerReference.deserialize("WIZARD:" + UUID.randomUUID()));
    }

    @Test
    void systemOwnerHasWellKnownServerId() {
        assertInstanceOf(SystemOwner.class, SystemOwner.server());
        assertEquals(new UUID(0L, 0L), SystemOwner.server().id());
    }
}
