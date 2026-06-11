package dev.rock.api.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RockPlayerTest {

    @Test
    void erasureRetainsUuidAndAnonymisesIdentity() {
        Instant now = Instant.now();
        RockPlayer player = new RockPlayer(
                UUID.randomUUID(), "Ahmed", Locale.ENGLISH, now.minusSeconds(3600), now, PlayerStatus.ACTIVE, null);
        assertTrue(player.active());

        RockPlayer erased = player.erased(now);

        assertEquals(player.id(), erased.id()); // retained for foreign keys (TRS §22)
        assertEquals(RockPlayer.ERASED_USERNAME, erased.username());
        assertEquals(PlayerStatus.DELETED, erased.status());
        assertEquals(now, erased.deletedAt());
        assertFalse(erased.active());
    }
}
