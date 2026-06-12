package dev.rock.moderation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DurationParserTest {

    @Test
    void parsesUnitsAndCombinations() {
        assertEquals(Duration.ofSeconds(45), DurationParser.parse("45s").orElseThrow());
        assertEquals(Duration.ofMinutes(30), DurationParser.parse("30m").orElseThrow());
        assertEquals(Duration.ofHours(2), DurationParser.parse("2h").orElseThrow());
        assertEquals(Duration.ofDays(7), DurationParser.parse("7d").orElseThrow());
        assertEquals(Duration.ofDays(14), DurationParser.parse("2w").orElseThrow());
        assertEquals(Duration.ofDays(1).plusHours(12), DurationParser.parse("1d12h").orElseThrow());
    }

    @Test
    void permanentFormsReturnEmpty() {
        assertTrue(DurationParser.parse("perm").isEmpty());
        assertTrue(DurationParser.parse("PERMANENT").isEmpty());
        assertTrue(DurationParser.parse("forever").isEmpty());
    }

    @Test
    void garbageIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("soon"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("10x"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("h"));
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse("5m extra"));
    }
}
