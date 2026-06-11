package dev.rock.data.jdbi;

/** Wraps checked SQL exceptions crossing the DataService boundary. */
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
