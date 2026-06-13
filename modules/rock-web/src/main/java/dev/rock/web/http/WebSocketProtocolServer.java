package dev.rock.web.http;

import dev.rock.api.protocol.ProtocolGateway;
import dev.rock.api.protocol.ProtocolTransport;
import dev.rock.web.auth.JwtCodec;
import dev.rock.web.auth.JwtCodec.TokenType;
import dev.rock.web.auth.WebAccountRepository;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A zero-dependency RFC 6455 WebSocket server that carries the rock-protocol
 * feed to browser clients, and is itself the {@link ProtocolTransport} for those
 * connections. JDK-only (raw socket + SHA-1/Base64 handshake + frame codec),
 * virtual-thread per connection — matching rock-web's "HTTP server is
 * JDK-native, no web framework" ethos.
 *
 * <p>Auth: the client connects to {@code ws://host:port/?token=<accessJWT>}; the
 * token is verified and resolved to the account's linked player UUID. Inbound
 * binary frames are opaque rock-protocol frames fed to
 * {@link ProtocolGateway#receive}; outbound projections are written as binary
 * frames to every connection bound to that player.
 */
public final class WebSocketProtocolServer implements ProtocolTransport {

    private static final String MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    private static final Logger log = LoggerFactory.getLogger(WebSocketProtocolServer.class);

    private final int port;
    private final JwtCodec jwt;
    private final WebAccountRepository accounts;
    private final ProtocolGateway gateway;

    private final Map<UUID, Set<Conn>> byPlayer = new ConcurrentHashMap<>();
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private volatile ServerSocket serverSocket;
    private volatile boolean running;

    public WebSocketProtocolServer(int port, JwtCodec jwt, WebAccountRepository accounts,
            ProtocolGateway gateway) {
        this.port = port;
        this.jwt = jwt;
        this.accounts = accounts;
        this.gateway = gateway;
    }

    public void start() {
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            throw new IllegalStateException("Could not bind protocol WebSocket port " + port, e);
        }
        running = true;
        gateway.addTransport(this);
        Thread.ofVirtual().name("rock-ws-accept").start(this::acceptLoop);
        log.info("Protocol WebSocket feed on ws://0.0.0.0:{}", port);
    }

    /** The actual bound port (useful when constructed with port 0 in tests). */
    public int port() {
        return serverSocket.getLocalPort();
    }

    public void stop() {
        running = false;
        gateway.removeTransport(this);
        try {
            if (serverSocket != null) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
            // closing
        }
        workers.shutdownNow();
    }

    private void acceptLoop() {
        while (running) {
            try {
                Socket socket = serverSocket.accept();
                workers.submit(() -> handle(socket));
            } catch (IOException e) {
                if (running) {
                    log.debug("WebSocket accept failed", e);
                }
            }
        }
    }

    // --- ProtocolTransport --------------------------------------------------

    @Override
    public void send(UUID playerId, byte[] frame) {
        Set<Conn> conns = byPlayer.get(playerId);
        if (conns == null) {
            return;
        }
        for (Conn conn : conns) {
            conn.sendBinary(frame);
        }
    }

    // --- Connection handling ------------------------------------------------

    private void handle(Socket socket) {
        UUID playerId = null;
        Conn conn = null;
        try {
            socket.setTcpNoDelay(true);
            InputStream in = socket.getInputStream();
            OutputStream out = socket.getOutputStream();

            Map<String, String> request = readHttpRequest(in);
            String key = request.get("sec-websocket-key");
            playerId = authenticate(request.get("@token")).orElse(null);
            if (key == null || playerId == null) {
                writeHttp(out, "401 Unauthorized");
                socket.close();
                return;
            }
            // Complete the upgrade handshake.
            out.write(("HTTP/1.1 101 Switching Protocols\r\n"
                    + "Upgrade: websocket\r\nConnection: Upgrade\r\n"
                    + "Sec-WebSocket-Accept: " + acceptKey(key) + "\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            out.flush();

            conn = new Conn(out);
            byPlayer.computeIfAbsent(playerId, k -> new CopyOnWriteArraySet<>()).add(conn);
            readFrames(in, playerId);
        } catch (IOException e) {
            log.debug("WebSocket connection ended", e);
        } finally {
            if (playerId != null && conn != null) {
                Set<Conn> conns = byPlayer.get(playerId);
                if (conns != null) {
                    conns.remove(conn);
                    if (conns.isEmpty()) {
                        byPlayer.remove(playerId);
                    }
                }
            }
            try {
                socket.close();
            } catch (IOException ignored) {
                // closing
            }
        }
    }

    /** Resolve an access token to the account's linked player UUID. */
    private Optional<UUID> authenticate(String token) {
        if (token == null) {
            return Optional.empty();
        }
        return jwt.verify(token, TokenType.ACCESS)
                .flatMap(claims -> accounts.findByUsername(claims.subject()).join())
                .map(account -> account.playerId());
    }

    private Map<String, String> readHttpRequest(InputStream in) throws IOException {
        Map<String, String> out = new ConcurrentHashMap<>();
        StringBuilder line = new StringBuilder();
        boolean first = true;
        int c;
        int blankRun = 0;
        while ((c = in.read()) != -1) {
            if (c == '\r') {
                continue;
            }
            if (c == '\n') {
                if (line.isEmpty()) {
                    break; // end of headers
                }
                if (first) {
                    // GET /?token=XYZ HTTP/1.1  -> capture the token query param.
                    String[] parts = line.toString().split(" ");
                    if (parts.length >= 2 && parts[1].contains("token=")) {
                        String q = parts[1].substring(parts[1].indexOf("token=") + 6);
                        int amp = q.indexOf('&');
                        out.put("@token", amp >= 0 ? q.substring(0, amp) : q);
                    }
                    first = false;
                } else {
                    int colon = line.indexOf(":");
                    if (colon > 0) {
                        out.put(line.substring(0, colon).trim().toLowerCase(),
                                line.substring(colon + 1).trim());
                    }
                }
                line.setLength(0);
                if (++blankRun > 200) {
                    break; // header flood guard
                }
            } else {
                line.append((char) c);
                blankRun = 0;
            }
        }
        return out;
    }

    private void readFrames(InputStream in, UUID playerId) throws IOException {
        while (running) {
            int b0 = in.read();
            if (b0 == -1) {
                return;
            }
            int opcode = b0 & 0x0F;
            int b1 = in.read();
            if (b1 == -1) {
                return;
            }
            boolean masked = (b1 & 0x80) != 0;
            long len = b1 & 0x7F;
            if (len == 126) {
                len = ((long) in.read() << 8) | in.read();
            } else if (len == 127) {
                len = 0;
                for (int i = 0; i < 8; i++) {
                    len = (len << 8) | in.read();
                }
            }
            byte[] mask = new byte[4];
            if (masked) {
                readFully(in, mask);
            }
            byte[] payload = new byte[(int) len];
            readFully(in, payload);
            if (masked) {
                for (int i = 0; i < payload.length; i++) {
                    payload[i] ^= mask[i & 3];
                }
            }
            switch (opcode) {
                case 0x8 -> {
                    return; // close
                }
                case 0x9 -> { /* ping — ignore (no pong needed for our control) */ }
                case 0x1, 0x2 -> gateway.receive(playerId, payload); // text/binary frame
                default -> { /* ignore */ }
            }
        }
    }

    private static void readFully(InputStream in, byte[] buf) throws IOException {
        int off = 0;
        while (off < buf.length) {
            int r = in.read(buf, off, buf.length - off);
            if (r == -1) {
                throw new IOException("stream closed mid-frame");
            }
            off += r;
        }
    }

    private static void writeHttp(OutputStream out, String status) throws IOException {
        out.write(("HTTP/1.1 " + status + "\r\nContent-Length: 0\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        out.flush();
    }

    private static String acceptKey(String key) {
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            byte[] hash = sha1.digest((key + MAGIC).getBytes(StandardCharsets.US_ASCII));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 unavailable", e);
        }
    }

    /** One client connection's output side, with a per-connection write lock. */
    private static final class Conn {
        private final OutputStream out;

        Conn(OutputStream out) {
            this.out = out;
        }

        void sendBinary(byte[] data) {
            synchronized (this) {
                try {
                    out.write(0x82); // FIN + binary opcode
                    if (data.length < 126) {
                        out.write(data.length);
                    } else if (data.length < 65536) {
                        out.write(126);
                        out.write((data.length >> 8) & 0xFF);
                        out.write(data.length & 0xFF);
                    } else {
                        out.write(127);
                        for (int i = 7; i >= 0; i--) {
                            out.write((int) ((long) data.length >> (8 * i)) & 0xFF);
                        }
                    }
                    out.write(data);
                    out.flush();
                } catch (IOException e) {
                    // Connection gone; the read loop will clean it up.
                }
            }
        }
    }
}
