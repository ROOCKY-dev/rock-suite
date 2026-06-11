package dev.rock.api.data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Neutral, driver-independent view of a result-set row. Keeps JDBC and JDBI
 * types out of module code (TRS §5 — modules communicate through DataService only).
 * Getters return {@code null} for SQL NULL.
 */
public interface RowView {

    String getString(String column);

    Integer getInt(String column);

    Long getLong(String column);

    Double getDouble(String column);

    Boolean getBoolean(String column);

    BigDecimal getBigDecimal(String column);

    /** UUIDs are stored as canonical 36-char strings. */
    UUID getUuid(String column);

    /** Instants are stored as epoch milliseconds. */
    Instant getInstant(String column);

    byte[] getBytes(String column);
}
