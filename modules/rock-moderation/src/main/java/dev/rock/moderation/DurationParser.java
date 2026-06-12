package dev.rock.moderation;

import dev.rock.api.annotations.RockInternal;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses command durations: "30m", "2h", "7d", "perm"/"permanent" → empty. */
@RockInternal
final class DurationParser {

    private static final Pattern PATTERN = Pattern.compile("(\\d+)([smhdw])");

    private DurationParser() {
    }

    /**
     * @return the parsed duration; empty for permanent
     * @throws IllegalArgumentException on unparseable input
     */
    static Optional<Duration> parse(String input) {
        String trimmed = input.trim().toLowerCase();
        if (trimmed.equals("perm") || trimmed.equals("permanent") || trimmed.equals("forever")) {
            return Optional.empty();
        }
        Matcher matcher = PATTERN.matcher(trimmed);
        Duration total = Duration.ZERO;
        int matchedLength = 0;
        while (matcher.find()) {
            long amount = Long.parseLong(matcher.group(1));
            total = total.plus(switch (matcher.group(2)) {
                case "s" -> Duration.ofSeconds(amount);
                case "m" -> Duration.ofMinutes(amount);
                case "h" -> Duration.ofHours(amount);
                case "d" -> Duration.ofDays(amount);
                case "w" -> Duration.ofDays(amount * 7);
                default -> throw new IllegalArgumentException("Unknown unit");
            });
            matchedLength += matcher.group().length();
        }
        if (total.isZero() || matchedLength != trimmed.length()) {
            throw new IllegalArgumentException(
                    "Invalid duration '" + input + "' — use e.g. 30m, 2h, 7d, 1w or perm");
        }
        return Optional.of(total);
    }
}
