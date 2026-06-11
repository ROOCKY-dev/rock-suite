package dev.rock.api.data;

/** Maps one result row to a domain object. */
@FunctionalInterface
public interface RowMapper<T> {

    T map(RowView row);
}
