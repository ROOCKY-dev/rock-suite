package dev.rock.web.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.rock.api.event.EventBus;
import dev.rock.core.event.DefaultEventBus;
import dev.rock.core.service.DefaultServiceRegistry;
import dev.rock.data.jdbi.JdbiDataService;
import dev.rock.data.migration.DataMigrator;
import dev.rock.protocol.ProtocolCodec;
import dev.rock.protocol.ProtocolHub;
import dev.rock.protocol.ProtocolMessage;
import dev.rock.web.auth.JwtCodec;
import dev.rock.web.auth.JwtCodec.TokenType;
import dev.rock.web.auth.PasswordHasher;
import dev.rock.web.auth.WebAccount.WebRole;
import dev.rock.web.auth.WebAccountRepository;
import dev.rock.web.auth.WebAuthService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.jdbi.v3.core.Jdbi;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Boots the real WebSocket protocol feed on an ephemeral port, connects a JDK
 * WebSocket client authenticated by a JWT, and verifies the rock-protocol round
 * trip (handshake + intent) over the browser transport — the web counterpart to
 * the on-server protocol bot.
 */
class WebSocketProtocolServerTest {

    @TempDir
    Path tempDir;

    private HikariDataSource dataSource;
    private JdbiDataService data;
    private ProtocolHub hub;
    private WebSocketProtocolServer ws;
    private final HttpClient client = HttpClient.newHttpClient();

    private final UUID playerId = UUID.randomUUID();
    private String token;

    @BeforeEach
    void setUp() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl("jdbc:sqlite:" + tempDir.resolve("ws.db"));
        config.setMaximumPoolSize(1);
        dataSource = new HikariDataSource(config);
        new DataMigrator(dataSource).migrate();
        data = new JdbiDataService(Jdbi.create(dataSource));

        EventBus eventBus = new DefaultEventBus(Executors.newVirtualThreadPerTaskExecutor());
        DefaultServiceRegistry services = new DefaultServiceRegistry();
        WebAccountRepository accounts = new WebAccountRepository(data);
        JwtCodec jwt = new JwtCodec("ws-integration-test-secret-value");

        // A web account linked to a Minecraft player; its access token authenticates the socket.
        WebAuthService auth = new WebAuthService(accounts, new PasswordHasher(), jwt,
                Duration.ofMinutes(15), Duration.ofDays(7));
        auth.register("alice", "alice-pw".toCharArray(), WebRole.USER, playerId);
        token = jwt.issue("alice", "USER", TokenType.ACCESS, Duration.ofMinutes(5));

        hub = new ProtocolHub(eventBus, services);
        hub.onEnable();

        ws = new WebSocketProtocolServer(0, jwt, accounts, hub);
        ws.start();
    }

    @AfterEach
    void tearDown() {
        ws.stop();
        hub.onDisable();
        data.onDisable();
        dataSource.close();
    }

    @Test
    void handshakeAndPingRoundTripOverWebSocket() throws Exception {
        BlockingQueue<byte[]> inbox = new LinkedBlockingQueue<>();
        WebSocket socket = client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:" + ws.port() + "/?token=" + token),
                        new WebSocket.Listener() {
                            @Override
                            public CompletionStage<?> onBinary(WebSocket webSocket, ByteBuffer data, boolean last) {
                                byte[] b = new byte[data.remaining()];
                                data.get(b);
                                inbox.add(b);
                                webSocket.request(1);
                                return null;
                            }
                        })
                .get(10, TimeUnit.SECONDS);

        // Handshake: Hello -> Welcome over the WebSocket.
        socket.sendBinary(ByteBuffer.wrap(
                ProtocolCodec.encode(new ProtocolMessage.Hello(1, List.of("WALLET")))), true);
        ProtocolMessage welcome = take(inbox);
        assertNotNull(welcome, "received a Welcome frame");
        assertTrue(welcome instanceof ProtocolMessage.Welcome);
        assertEquals(List.of("WALLET"), ((ProtocolMessage.Welcome) welcome).grantedCapabilities());

        // Intent: session.ping -> session.pong, nonce echoed.
        socket.sendBinary(ByteBuffer.wrap(
                ProtocolCodec.encode(new ProtocolMessage.Intent("session.ping", java.util.Map.of("nonce", "ws-9")))), true);
        ProtocolMessage pong = take(inbox);
        assertNotNull(pong, "received a pong frame");
        ProtocolMessage.Projection p = (ProtocolMessage.Projection) pong;
        assertEquals("session.pong", p.type());
        assertEquals("ws-9", p.field("nonce"));

        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
    }

    private ProtocolMessage take(BlockingQueue<byte[]> inbox) throws InterruptedException {
        byte[] frame = inbox.poll(8, TimeUnit.SECONDS);
        if (frame == null) {
            return null;
        }
        Optional<ProtocolMessage> decoded = ProtocolCodec.decode(frame);
        return decoded.orElse(null);
    }
}
