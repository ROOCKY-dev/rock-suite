package dev.rock.discord;

import java.util.concurrent.CompletableFuture;

/**
 * Transport abstraction for Discord delivery. v1 ships a REST transport on
 * the JDK http client (Architectural Review C-3); a WebSocket gateway can be
 * added behind this interface without touching the queue or service.
 */
public interface DiscordGateway {

    CompletableFuture<Void> send(String channelId, String content);
}
