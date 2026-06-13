package dev.rock.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.rock.api.event.EventBus;
import dev.rock.core.event.DefaultEventBus;
import dev.rock.core.service.DefaultServiceRegistry;
import dev.rock.data.jdbi.JdbiDataService;
import dev.rock.data.migration.DataMigrator;
import dev.rock.web.auth.JwtCodec;
import dev.rock.web.auth.PasswordHasher;
import dev.rock.web.auth.WebAccount.WebRole;
import dev.rock.web.auth.WebAccountRepository;
import dev.rock.web.auth.WebAuthService;
import dev.rock.web.http.Json;
import dev.rock.web.http.SseHub;
import dev.rock.web.http.WebServer;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.Executors;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Boots the real web server on an ephemeral port and drives it over HTTP. */
class WebServerIntegrationTest {

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private JdbiDataService data;
    private WebServer server;
    private SseHub sseHub;
    private final HttpClient client = HttpClient.newHttpClient();
    private String base;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("web.db"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        new DataMigrator(dataSource).migrate();
        data = new JdbiDataService(Jdbi.create(dataSource));

        EventBus eventBus = new DefaultEventBus(Executors.newVirtualThreadPerTaskExecutor());
        DefaultServiceRegistry services = new DefaultServiceRegistry();
        WebAccountRepository accounts = new WebAccountRepository(data);
        JwtCodec jwt = new JwtCodec("integration-test-secret-key-value");
        WebAuthService auth = new WebAuthService(accounts, new PasswordHasher(), jwt,
                Duration.ofMinutes(15), Duration.ofDays(7));
        // Seed an admin and a plain user.
        auth.register("admin", "s3cret-admin".toCharArray(), WebRole.ADMIN, null);
        auth.register("alice", "alice-pw".toCharArray(), WebRole.USER, null);

        sseHub = new SseHub(eventBus);
        sseHub.onEnable();
        server = new WebServer(0, jwt, new WebRoutes(auth, services).routes(), sseHub);
        server.start();
        base = "http://127.0.0.1:" + server.port();
    }

    @AfterEach
    void tearDown() {
        server.stop();
        sseHub.onDisable();
        data.onDisable();
        dataSource.close();
    }

    private HttpResponse<String> post(String path, String jsonBody, String bearer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        if (bearer != null) {
            b.header("Authorization", "Bearer " + bearer);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> get(String path, String bearer) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path)).GET();
        if (bearer != null) {
            b.header("Authorization", "Bearer " + bearer);
        }
        return client.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    private String login(String user, String pw) throws Exception {
        HttpResponse<String> res = post("/api/v1/auth/login",
                "{\"username\":\"" + user + "\",\"password\":\"" + pw + "\"}", null);
        assertEquals(200, res.statusCode());
        return Json.MAPPER.readTree(res.body()).get("accessToken").asText();
    }

    @Test
    void loginReturnsTokensAndProtectsEndpoints() throws Exception {
        // No token → 401.
        assertEquals(401, get("/api/v1/me", null).statusCode());

        // Bad credentials → 401.
        assertEquals(401, post("/api/v1/auth/login",
                "{\"username\":\"alice\",\"password\":\"wrong\"}", null).statusCode());

        // Good login → token → /me works.
        String token = login("alice", "alice-pw");
        HttpResponse<String> me = get("/api/v1/me", token);
        assertEquals(200, me.statusCode());
        assertEquals("alice", Json.MAPPER.readTree(me.body()).get("user").asText());
    }

    @Test
    void adminEndpointForbiddenToPlainUser() throws Exception {
        String userToken = login("alice", "alice-pw");
        String adminToken = login("admin", "s3cret-admin");

        assertEquals(403, get("/api/v1/audit", userToken).statusCode(), "USER cannot read audit");
        assertEquals(200, get("/api/v1/audit", adminToken).statusCode(), "ADMIN can");
    }

    @Test
    void refreshTokenMintsFreshAccessToken() throws Exception {
        HttpResponse<String> loginRes = post("/api/v1/auth/login",
                "{\"username\":\"alice\",\"password\":\"alice-pw\"}", null);
        String refresh = Json.MAPPER.readTree(loginRes.body()).get("refreshToken").asText();

        HttpResponse<String> refreshed = post("/api/v1/auth/refresh",
                "{\"refreshToken\":\"" + refresh + "\"}", null);
        assertEquals(200, refreshed.statusCode());
        String newAccess = Json.MAPPER.readTree(refreshed.body()).get("accessToken").asText();
        assertEquals(200, get("/api/v1/me", newAccess).statusCode());
    }

    @Test
    void readEndpointsReturnJsonShape() throws Exception {
        String token = login("alice", "alice-pw");

        JsonNode players = Json.MAPPER.readTree(get("/api/v1/players", token).body());
        assertTrue(players.has("online"));
        JsonNode baltop = Json.MAPPER.readTree(get("/api/v1/economy/baltop", token).body());
        assertTrue(baltop.has("top"));
    }

    @Test
    void unknownPathIs404() throws Exception {
        assertEquals(404, get("/api/v1/nope", login("alice", "alice-pw")).statusCode());
    }
}
