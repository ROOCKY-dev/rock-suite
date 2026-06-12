package dev.rock.api.domain;

import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/**
 * Context scoping for permissions (FTB Ranks conditions / LuckPerms contexts).
 * A node carrying a context set applies only when every pair is present in the
 * query context. The empty set is the global context and always applies.
 *
 * <p>Conventional keys: {@code world}, {@code dimension}, {@code server},
 * {@code gamemode}, and ROCK's edge: {@code claim} (claim id) and
 * {@code team}. Serialised canonically as {@code "k1=v1;k2=v2"} with sorted keys.
 */
public record ContextSet(Map<String, String> pairs) {

    private static final ContextSet EMPTY = new ContextSet(Map.of());

    public ContextSet {
        pairs = Map.copyOf(new TreeMap<>(pairs));
    }

    public static ContextSet empty() {
        return EMPTY;
    }

    public static ContextSet of(String key, String value) {
        return new ContextSet(Map.of(key, value));
    }

    public static ContextSet of(String k1, String v1, String k2, String v2) {
        return new ContextSet(Map.of(k1, v1, k2, v2));
    }

    public boolean isEmpty() {
        return pairs.isEmpty();
    }

    /** Number of pairs — used as evaluation specificity (more specific wins). */
    public int specificity() {
        return pairs.size();
    }

    /** True when every pair of this set is present in the query context. */
    public boolean satisfiedBy(ContextSet query) {
        Objects.requireNonNull(query, "query");
        for (Map.Entry<String, String> pair : pairs.entrySet()) {
            if (!pair.getValue().equals(query.pairs().get(pair.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /** Canonical DB form: sorted {@code "k1=v1;k2=v2"}; empty string for global. */
    public String serialize() {
        if (pairs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        new TreeMap<>(pairs).forEach((k, v) -> {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(k).append('=').append(v);
        });
        return sb.toString();
    }

    public static ContextSet deserialize(String value) {
        if (value == null || value.isEmpty()) {
            return EMPTY;
        }
        Map<String, String> pairs = new TreeMap<>();
        for (String pair : value.split(";")) {
            int eq = pair.indexOf('=');
            if (eq < 1) {
                throw new IllegalArgumentException("Invalid context pair: " + pair);
            }
            pairs.put(pair.substring(0, eq), pair.substring(eq + 1));
        }
        return new ContextSet(pairs);
    }
}
