package dev.rock.discord;

import dev.rock.api.annotations.RockInternal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

/**
 * Discord REST transport over the JDK http client — no external Discord SDK
 * in the dependency tree (Architectural Review C-3).
 */
@RockInternal
public final class HttpDiscordGateway implements DiscordGateway {

    private static final String API_BASE = "https://discord.com/api/v10";

    private final HttpClient client;
    private final String botToken;

    public HttpDiscordGateway(String botToken) {
        this.botToken = botToken;
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public CompletableFuture<Void> send(String channelId, String content) {
        String body = "{\"content\":" + jsonString(content) + "}";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(API_BASE + "/channels/" + channelId + "/messages"))
                .header("Authorization", "Bot " + botToken)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(15))
                .build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(response -> {
                    if (response.statusCode() >= 400) {
                        throw new IllegalStateException(
                                "Discord API returned " + response.statusCode() + ": " + response.body());
                    }
                });
    }

    private static String jsonString(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
