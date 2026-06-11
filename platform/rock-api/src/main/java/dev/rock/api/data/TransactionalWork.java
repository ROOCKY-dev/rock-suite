package dev.rock.api.data;

/** Unit of work executed atomically inside a database transaction. */
@FunctionalInterface
public interface TransactionalWork<T> {

    T execute(TransactionContext tx) throws Exception;
}
