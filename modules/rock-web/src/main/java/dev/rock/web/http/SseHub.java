package dev.rock.web.http;

import com.sun.net.httpserver.HttpExchange;
import dev.rock.api.annotations.RockInternal;
import dev.rock.api.event.EventBus;
import dev.rock.api.event.EventPriority;
import dev.rock.api.event.Subscription;
import dev.rock.api.events.claim.ClaimCreatedEvent;
import dev.rock.api.events.economy.TransactionCreatedEvent;
import dev.rock.api.events.player.PlayerJoinEvent;
import dev.rock.api.events.player.PlayerLeaveEvent;
import dev.rock.api.events.punishment.PunishmentAppliedEvent;
import dev.rock.api.lifecycle.LifecycleAware;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Server-Sent Events feed (TRS §12 real-time updates). A dependency-free,
 * proxy-friendly push transport for the dashboard: domain events become
 * {@code data:} lines streamed to every connected client. (WebSocket can be
 * added later behind the same projection set; SSE covers server→client push
 * cleanly with zero deps.)
 *
 * <p>The same domain events the rest of the platform already publishes — joins,
 * claims, transactions, punishments — become a live admin activity feed.
 */
@RockInternal
public final class SseHub implements LifecycleAware {

    private static final Logger log = LoggerFactory.getLogger(SseHub.class);

    private final EventBus eventBus;
    private final List<OutputStream> clients = new CopyOnWriteArrayList<>();
    private final List<Subscription> subscriptions = new java.util.ArrayList<>();

    public SseHub(EventBus eventBus) {
        this.eventBus = eventBus;
    }

    /** Upgrades an HTTP request to an SSE stream; requires an authenticated principal. */
    public void handle(HttpExchange exchange, Object principal) throws IOException {
        if (principal == null) {
            exchange.sendResponseHeaders(401, -1);
            exchange.close();
            return;
        }
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
        OutputStream out = exchange.getResponseBody();
        out.write(": connected\n\n".getBytes(StandardCharsets.UTF_8));
        out.flush();
        clients.add(out);
    }

    int clientCount() {
        return clients.size();
    }

    void broadcast(String eventType, Map<String, String> fields) {
        StringBuilder sb = new StringBuilder("event: ").append(eventType).append("\ndata: ");
        sb.append(Json.write(fields).length == 0 ? "{}" : new String(Json.write(fields), StandardCharsets.UTF_8));
        sb.append("\n\n");
        byte[] frame = sb.toString().getBytes(StandardCharsets.UTF_8);
        clients.removeIf(out -> {
            try {
                out.write(frame);
                out.flush();
                return false;
            } catch (IOException e) {
                return true; // client gone — drop it
            }
        });
    }

    @Override
    public void onEnable() {
        subscriptions.add(eventBus.subscribe(PlayerJoinEvent.class, EventPriority.LAST, e ->
                broadcast("player.join", Map.of("player", e.player().username(),
                        "firstJoin", Boolean.toString(e.firstJoin())))));
        subscriptions.add(eventBus.subscribe(PlayerLeaveEvent.class, EventPriority.LAST, e ->
                broadcast("player.leave", Map.of("player", e.player().username()))));
        subscriptions.add(eventBus.subscribe(ClaimCreatedEvent.class, EventPriority.LAST, e ->
                broadcast("claim.created", Map.of("name", e.claim().displayName(),
                        "owner", e.claim().owner().serialize()))));
        subscriptions.add(eventBus.subscribe(TransactionCreatedEvent.class, EventPriority.LAST, e ->
                broadcast("economy.transaction", Map.of("amount", e.transaction().amount().toPlainString(),
                        "reason", e.transaction().reason()))));
        subscriptions.add(eventBus.subscribe(PunishmentAppliedEvent.class, EventPriority.LAST, e ->
                broadcast("moderation.punishment", Map.of("type", e.punishment().type().name(),
                        "reason", e.punishment().reason()))));
        log.info("Web SSE feed active (player/claim/economy/moderation events)");
    }

    @Override
    public void onDisable() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
        clients.forEach(out -> {
            try {
                out.close();
            } catch (IOException ignored) {
                // closing a dead stream — fine
            }
        });
        clients.clear();
    }
}
