package dev.rock.data.jdbi;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.data.RowView;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

/**
 * RowView over a JDBC ResultSet. Storage conventions: UUIDs as canonical
 * 36-char strings, Instants as epoch milliseconds (portable across SQLite,
 * PostgreSQL, and MariaDB).
 */
@RockInternal
public final class ResultSetRowView implements RowView {

    private final ResultSet resultSet;

    public ResultSetRowView(ResultSet resultSet) {
        this.resultSet = resultSet;
    }

    private <T> T get(String column, SqlGetter<T> getter) {
        try {
            T value = getter.get(column);
            return resultSet.wasNull() ? null : value;
        } catch (SQLException e) {
            throw new DataAccessException("Failed to read column " + column, e);
        }
    }

    @FunctionalInterface
    private interface SqlGetter<T> {
        T get(String column) throws SQLException;
    }

    @Override
    public String getString(String column) {
        return get(column, resultSet::getString);
    }

    @Override
    public Integer getInt(String column) {
        return get(column, resultSet::getInt);
    }

    @Override
    public Long getLong(String column) {
        return get(column, resultSet::getLong);
    }

    @Override
    public Double getDouble(String column) {
        return get(column, resultSet::getDouble);
    }

    @Override
    public Boolean getBoolean(String column) {
        return get(column, resultSet::getBoolean);
    }

    @Override
    public BigDecimal getBigDecimal(String column) {
        return get(column, resultSet::getBigDecimal);
    }

    @Override
    public UUID getUuid(String column) {
        String value = getString(column);
        return value == null ? null : UUID.fromString(value);
    }

    @Override
    public Instant getInstant(String column) {
        Long value = getLong(column);
        return value == null ? null : Instant.ofEpochMilli(value);
    }

    @Override
    public byte[] getBytes(String column) {
        return get(column, resultSet::getBytes);
    }
}
