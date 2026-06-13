package dev.rock.web.http;

import dev.rock.api.annotations.RockInternal;
import dev.rock.web.auth.WebAccount.WebRole;
import java.io.IOException;

/** A single REST route: method + path + required role (null = public) + handler. */
@RockInternal
public record Route(String method, String path, WebRole requiredRole, Handler handler) {

    @FunctionalInterface
    public interface Handler {
        /** Returns the response; may read the request body / principal. */
        Response handle(Request request) throws IOException;
    }
}
