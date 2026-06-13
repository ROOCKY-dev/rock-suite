package dev.rock.web;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.config.ConfigEngine;
import dev.rock.api.event.EventBus;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.web.auth.JwtCodec;
import dev.rock.web.auth.PasswordHasher;
import dev.rock.web.auth.WebAccount.WebRole;
import dev.rock.web.auth.WebAccountRepository;
import dev.rock.web.auth.WebAuthService;
import dev.rock.web.http.SseHub;
import dev.rock.web.http.WebServer;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Boots the web dashboard backend (TRS §12): REST {@code /api/v1} + JWT auth +
 * Argon2id passwords + an SSE event feed. On first run, if no web accounts
 * exist and a bootstrap admin password is configured, creates the admin account.
 */
@RockInternal
@Singleton
public final class WebModuleService implements LifecycleAware {

    static final String DEFAULT_CONFIG = """
            # ROCK web dashboard (TRS §12)
            [web]
            enabled = true
            port = 8080
            # HMAC secret — set via environment, never commit:
            # jwt-secret = "${env.ROCK_WEB_JWT_SECRET}"
            access-token-minutes = 15
            refresh-token-days = 7
            # First-run admin (password via ${env.ROCK_WEB_ADMIN_PASSWORD}):
            bootstrap-admin = "admin"
            """;

    private static final Logger log = LoggerFactory.getLogger(WebModuleService.class);

    private final ConfigEngine configEngine;
    private final EventBus eventBus;
    private final ServiceRegistry services;
    private final WebAccountRepository accounts;

    private WebServer server;
    private SseHub sseHub;

    @Inject
    public WebModuleService(ConfigEngine configEngine, EventBus eventBus,
            ServiceRegistry services, WebAccountRepository accounts) {
        this.configEngine = configEngine;
        this.eventBus = eventBus;
        this.services = services;
        this.accounts = accounts;
    }

    @Override
    public void onEnable() {
        WebSettings settings = WebSettings.fromConfig(
                configEngine.loadModuleConfig("rock-web", DEFAULT_CONFIG));
        if (!settings.enabled()) {
            log.info("Web dashboard disabled by config");
            return;
        }

        PasswordHasher hasher = new PasswordHasher();
        JwtCodec jwt = new JwtCodec(settings.jwtSecret());
        WebAuthService auth = new WebAuthService(
                accounts, hasher, jwt, settings.accessTtl(), settings.refreshTtl());

        bootstrapAdmin(settings, auth);

        sseHub = new SseHub(eventBus);
        sseHub.onEnable();
        server = new WebServer(settings.port(), jwt,
                new WebRoutes(auth, services).routes(), sseHub);
        server.start();
    }

    private void bootstrapAdmin(WebSettings settings, WebAuthService auth) {
        if (settings.bootstrapAdminPassword().isBlank() || accounts.count().join() > 0) {
            return;
        }
        auth.register(settings.bootstrapAdmin(), settings.bootstrapAdminPassword().toCharArray(),
                WebRole.ADMIN, null);
        log.info("Created bootstrap web admin account '{}'", settings.bootstrapAdmin());
    }

    @Override
    public void onDisable() {
        if (server != null) {
            server.stop();
        }
        if (sseHub != null) {
            sseHub.onDisable();
        }
    }
}
