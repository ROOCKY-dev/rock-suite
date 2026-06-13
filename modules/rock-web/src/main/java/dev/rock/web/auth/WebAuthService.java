package dev.rock.web.auth;

import dev.rock.api.annotations.RockInternal;
import dev.rock.web.auth.JwtCodec.TokenType;
import dev.rock.web.auth.WebAccount.WebRole;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Registers and authenticates web accounts; issues access + refresh tokens. */
@RockInternal
public final class WebAuthService {

    /** Login result: short-lived access token + longer-lived refresh token. */
    public record Tokens(String accessToken, String refreshToken, String role) {
    }

    private final WebAccountRepository accounts;
    private final PasswordHasher hasher;
    private final JwtCodec jwt;
    private final Duration accessTtl;
    private final Duration refreshTtl;

    public WebAuthService(WebAccountRepository accounts, PasswordHasher hasher, JwtCodec jwt,
            Duration accessTtl, Duration refreshTtl) {
        this.accounts = accounts;
        this.hasher = hasher;
        this.jwt = jwt;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    /** Creates an account; fails if the username exists. */
    public WebAccount register(String username, char[] password, WebRole role, UUID playerId) {
        if (accounts.findByUsername(username).join().isPresent()) {
            throw new IllegalStateException("Username already taken: " + username);
        }
        WebAccount account = new WebAccount(UUID.randomUUID(), username, hasher.hash(password),
                role, playerId, Instant.now(), null);
        accounts.save(account).join();
        return account;
    }

    /** Verifies credentials and returns tokens, or empty on bad username/password. */
    public Optional<Tokens> login(String username, String password) {
        Optional<WebAccount> found = accounts.findByUsername(username).join();
        if (found.isEmpty() || !hasher.verify(password, found.get().passwordHash())) {
            return Optional.empty();
        }
        WebAccount account = found.get();
        accounts.touchLogin(account.id(), Instant.now());
        return Optional.of(issueTokens(account.username(), account.role().name()));
    }

    /** Exchanges a valid refresh token for a fresh token pair. */
    public Optional<Tokens> refresh(String refreshToken) {
        return jwt.verify(refreshToken, TokenType.REFRESH)
                .map(claims -> issueTokens(claims.subject(), claims.role()));
    }

    private Tokens issueTokens(String subject, String role) {
        return new Tokens(
                jwt.issue(subject, role, TokenType.ACCESS, accessTtl),
                jwt.issue(subject, role, TokenType.REFRESH, refreshTtl),
                role);
    }
}
