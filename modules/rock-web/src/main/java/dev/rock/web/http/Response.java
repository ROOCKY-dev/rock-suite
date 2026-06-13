package dev.rock.web.http;

import dev.rock.api.annotations.RockInternal;
import java.util.Map;

/** An HTTP response: status + JSON body (serialised by the server). */
@RockInternal
public record Response(int status, Object body) {

    public static Response ok(Object body) {
        return new Response(200, body);
    }

    public static Response status(int code, String message) {
        return new Response(code, Map.of("error", message));
    }

    public static Response unauthorized() {
        return status(401, "unauthorized");
    }

    public static Response forbidden() {
        return status(403, "forbidden");
    }

    public static Response notFound() {
        return status(404, "not found");
    }

    public static Response badRequest(String message) {
        return status(400, message);
    }
}
