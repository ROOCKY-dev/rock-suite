package dev.rock.web.http;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import dev.rock.api.annotations.RockInternal;
import dev.rock.web.auth.JwtCodec;
import java.io.IOException;
import java.util.Optional;

/** Per-request context handed to route handlers. */
@RockInternal
public final class Request {

    private final HttpExchange exchange;
    private byte[] cachedBody;
    private JwtCodec.Claims principal;

    Request(HttpExchange exchange) {
        this.exchange = exchange;
    }

    public String method() {
        return exchange.getRequestMethod();
    }

    public String path() {
        return exchange.getRequestURI().getPath();
    }

    public String query() {
        return exchange.getRequestURI().getQuery();
    }

    public Optional<String> header(String name) {
        return Optional.ofNullable(exchange.getRequestHeaders().getFirst(name));
    }

    public byte[] body() throws IOException {
        if (cachedBody == null) {
            cachedBody = exchange.getRequestBody().readAllBytes();
        }
        return cachedBody;
    }

    public JsonNode json() throws IOException {
        return Json.read(body());
    }

    /** Authenticated principal (set by the auth filter); null on public routes. */
    public JwtCodec.Claims principal() {
        return principal;
    }

    void setPrincipal(JwtCodec.Claims principal) {
        this.principal = principal;
    }
}
