package dev.rock.web.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.rock.api.annotations.RockInternal;
import dev.rock.web.auth.JwtCodec;
import dev.rock.web.auth.WebAccount.WebRole;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minimal REST server on the JDK {@link HttpServer} (zero third-party HTTP
 * dependency, consistent with rock-discord's stdlib-only transport). Routing is
 * exact-path; the JWT auth filter runs before any route requiring a role.
 * Requests are served on a virtual-thread executor — never the tick thread.
 */
@RockInternal
public final class WebServer {

    private static final Logger log = LoggerFactory.getLogger(WebServer.class);

    private final HttpServer server;
    private final JwtCodec jwt;
    private final List<Route> routes;
    private final SseHub sseHub;

    public WebServer(int port, JwtCodec jwt, List<Route> routes, SseHub sseHub) {
        this.jwt = jwt;
        this.routes = routes;
        this.sseHub = sseHub;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            throw new IllegalStateException("Could not bind web server to port " + port, e);
        }
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::dispatch);
    }

    /** Actual bound port (useful when constructed with port 0 in tests). */
    public int port() {
        return server.getAddress().getPort();
    }

    public void start() {
        server.start();
        log.info("ROCK web dashboard listening on :{}", port());
    }

    public void stop() {
        server.stop(0);
    }

    private void dispatch(HttpExchange exchange) throws IOException {
        cors(exchange);
        if (exchange.getRequestMethod().equals("OPTIONS")) {
            respond(exchange, 204, new byte[0]);
            return;
        }
        String path = exchange.getRequestURI().getPath();

        // The dashboard SPA + assets (everything that isn't the API) is static.
        if (exchange.getRequestMethod().equals("GET") && !path.startsWith("/api/")) {
            serveStatic(exchange, path);
            return;
        }

        // Server-Sent Events feed (real-time projections) is its own handler.
        if (path.equals("/api/v1/events")) {
            sseHub.handle(exchange, authenticate(exchange).orElse(null));
            return;
        }

        Route route = routes.stream()
                .filter(r -> r.method().equals(exchange.getRequestMethod()) && r.path().equals(path))
                .findFirst().orElse(null);
        if (route == null) {
            write(exchange, Response.notFound());
            return;
        }

        Request request = new Request(exchange);
        if (route.requiredRole() != null) {
            Optional<JwtCodec.Claims> principal = authenticate(exchange);
            if (principal.isEmpty()) {
                write(exchange, Response.unauthorized());
                return;
            }
            if (route.requiredRole() == WebRole.ADMIN && !principal.get().role().equals("ADMIN")) {
                write(exchange, Response.forbidden());
                return;
            }
            request.setPrincipal(principal.get());
        }

        try {
            write(exchange, route.handler().handle(request));
        } catch (Exception e) {
            log.error("Handler for {} {} failed", exchange.getRequestMethod(), path, e);
            write(exchange, Response.status(500, "internal error"));
        }
    }

    private Optional<JwtCodec.Claims> authenticate(HttpExchange exchange) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return jwt.verify(header.substring("Bearer ".length()), JwtCodec.TokenType.ACCESS);
        }
        // EventSource (SSE) can't set headers, so accept a ?token= query param too.
        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String pair : query.split("&")) {
                if (pair.startsWith("token=")) {
                    return jwt.verify(pair.substring("token=".length()), JwtCodec.TokenType.ACCESS);
                }
            }
        }
        return Optional.empty();
    }

    private void serveStatic(HttpExchange exchange, String path) throws IOException {
        String resource = path.equals("/") ? "web/index.html" : "web" + path;
        if (resource.contains("..")) {
            respond(exchange, 403, new byte[0]);
            return;
        }
        byte[] body = readResource(resource);
        if (body == null) {
            // SPA fallback: unknown non-asset routes render the app shell.
            body = readResource("web/index.html");
            resource = "web/index.html";
        }
        if (body == null) {
            write(exchange, Response.notFound());
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", contentType(resource));
        respond(exchange, 200, body);
    }

    private static byte[] readResource(String resource) throws IOException {
        try (var in = WebServer.class.getClassLoader().getResourceAsStream(resource)) {
            return in == null ? null : in.readAllBytes();
        }
    }

    private static String contentType(String resource) {
        if (resource.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (resource.endsWith(".js")) {
            return "application/javascript; charset=utf-8";
        }
        if (resource.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        if (resource.endsWith(".svg")) {
            return "image/svg+xml";
        }
        return "text/plain; charset=utf-8";
    }

    private void write(HttpExchange exchange, Response response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        respond(exchange, response.status(), Json.write(response.body()));
    }

    private static void respond(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
        if (body.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } else {
            exchange.close();
        }
    }

    private static void cors(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    }

    static String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
