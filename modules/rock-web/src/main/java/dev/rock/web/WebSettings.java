package dev.rock.web;

import dev.rock.api.config.RockConfig;
import java.time.Duration;

/**
 * rock-web.toml settings.
 *
 * @param port        HTTP listen port
 * @param jwtSecret   HMAC secret — inject via ${env.ROCK_WEB_JWT_SECRET} (TRS §11)
 * @param bootstrapAdmin first-run admin username; an account is auto-created with
 *                    password from ${env.ROCK_WEB_ADMIN_PASSWORD} if none exist
 */
public record WebSettings(
        boolean enabled,
        int port,
        int protocolPort,
        String jwtSecret,
        Duration accessTtl,
        Duration refreshTtl,
        String bootstrapAdmin,
        String bootstrapAdminPassword) {

    public static WebSettings fromConfig(RockConfig config) {
        int httpPort = config.getInt("web.port", 8080);
        return new WebSettings(
                config.getBoolean("web.enabled", true),
                httpPort,
                // The protocol WebSocket feed; defaults to the HTTP port + 1.
                config.getInt("web.protocol-port", httpPort + 1),
                config.getString("web.jwt-secret", "change-me-rock-web-dev-secret-0001"),
                Duration.ofMinutes(config.getLong("web.access-token-minutes", 15)),
                Duration.ofDays(config.getLong("web.refresh-token-days", 7)),
                config.getString("web.bootstrap-admin", "admin"),
                config.getString("web.bootstrap-admin-password", ""));
    }
}
