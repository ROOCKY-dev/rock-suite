package dev.rock.web;

import com.fasterxml.jackson.databind.JsonNode;
import dev.rock.api.annotations.RockInternal;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.api.services.AuditService;
import dev.rock.api.services.ClaimService;
import dev.rock.api.services.EconomyService;
import dev.rock.api.services.PlayerService;
import dev.rock.web.auth.WebAccount.WebRole;
import dev.rock.web.auth.WebAuthService;
import dev.rock.web.http.Response;
import dev.rock.web.http.Route;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The dashboard REST surface (TRS §12), versioned from day one under
 * {@code /api/v1} (TRS §12 API Versioning). Read endpoints project the same
 * services the in-game commands use — one data model, two faces.
 */
@RockInternal
public final class WebRoutes {

    private final WebAuthService auth;
    private final ServiceRegistry services;

    public WebRoutes(WebAuthService auth, ServiceRegistry services) {
        this.auth = auth;
        this.services = services;
    }

    public List<Route> routes() {
        List<Route> routes = new ArrayList<>();

        // --- Auth (public) --------------------------------------------------
        routes.add(new Route("POST", "/api/v1/auth/login", null, request -> {
            JsonNode body = request.json();
            String username = text(body, "username");
            String password = text(body, "password");
            if (username == null || password == null) {
                return Response.badRequest("username and password required");
            }
            return auth.login(username, password)
                    .map(tokens -> Response.ok(Map.of(
                            "accessToken", tokens.accessToken(),
                            "refreshToken", tokens.refreshToken(),
                            "role", tokens.role())))
                    .orElse(Response.status(401, "invalid credentials"));
        }));

        routes.add(new Route("POST", "/api/v1/auth/refresh", null, request -> {
            String refreshToken = text(request.json(), "refreshToken");
            if (refreshToken == null) {
                return Response.badRequest("refreshToken required");
            }
            return auth.refresh(refreshToken)
                    .map(tokens -> Response.ok(Map.of(
                            "accessToken", tokens.accessToken(),
                            "refreshToken", tokens.refreshToken(),
                            "role", tokens.role())))
                    .orElse(Response.unauthorized());
        }));

        // --- Read endpoints (any authenticated user) ------------------------
        routes.add(new Route("GET", "/api/v1/me", WebRole.USER, request ->
                Response.ok(Map.of("user", request.principal().subject(), "role", request.principal().role()))));

        routes.add(new Route("GET", "/api/v1/players", WebRole.USER, request ->
                services.find(PlayerService.class)
                        .map(players -> Response.ok(Map.of("online", players.online().join().stream()
                                .map(p -> Map.of("id", p.id().toString(), "username", p.username())).toList())))
                        .orElse(Response.ok(Map.of("online", List.of())))));

        routes.add(new Route("GET", "/api/v1/economy/baltop", WebRole.USER, request ->
                services.find(EconomyService.class)
                        .map(economy -> Response.ok(Map.of("top", economy.topBalances(10).join().stream()
                                .map(a -> Map.of("owner", a.owner().serialize(),
                                        "balance", a.balance().toPlainString())).toList())))
                        .orElse(Response.ok(Map.of("top", List.of())))));

        // --- Admin endpoints ------------------------------------------------
        routes.add(new Route("GET", "/api/v1/audit", WebRole.ADMIN, request ->
                services.find(AuditService.class)
                        .map(audit -> Response.ok(Map.of("entries", audit.recent(50).join().stream()
                                .map(e -> Map.of("action", e.action().name(), "target", e.targetType(),
                                        "actor", e.actor().serialize(),
                                        "timestamp", e.timestamp().toString())).toList())))
                        .orElse(Response.ok(Map.of("entries", List.of())))));

        return routes;
    }

    private static String text(JsonNode node, String field) {
        return Optional.ofNullable(node).map(n -> n.get(field))
                .filter(JsonNode::isTextual).map(JsonNode::asText).orElse(null);
    }

    // ClaimService reserved for the claims map endpoint (Tier-1 client / web).
    @SuppressWarnings("unused")
    private Optional<ClaimService> claims() {
        return services.find(ClaimService.class);
    }
}
