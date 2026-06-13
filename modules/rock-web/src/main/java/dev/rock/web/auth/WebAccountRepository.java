package dev.rock.web.auth;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.data.DataService;
import dev.rock.api.data.RowMapper;
import dev.rock.web.auth.WebAccount.WebRole;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Web-account storage on the platform DataService (no JDBC in module code, TRS §5). */
@RockInternal
@Singleton
public final class WebAccountRepository {

    private static final RowMapper<WebAccount> MAPPER = row -> new WebAccount(
            row.getUuid("id"),
            row.getString("username"),
            row.getString("password_hash"),
            WebRole.valueOf(row.getString("role")),
            row.getUuid("player_id"),
            row.getInstant("created"),
            row.getInstant("last_login"));

    private final DataService data;

    @Inject
    public WebAccountRepository(DataService data) {
        this.data = data;
    }

    public CompletableFuture<Void> save(WebAccount account) {
        Map<String, Object> params = new HashMap<>();
        params.put("id", account.id().toString());
        params.put("username", account.username());
        params.put("hash", account.passwordHash());
        params.put("role", account.role().name());
        params.put("player", account.playerId() == null ? null : account.playerId().toString());
        params.put("created", account.created().toEpochMilli());
        params.put("last", account.lastLogin() == null ? null : account.lastLogin().toEpochMilli());
        return data.update("""
                INSERT INTO rock_web_accounts (id, username, password_hash, role, player_id, created, last_login)
                VALUES (:id, :username, :hash, :role, :player, :created, :last)
                """, params).thenApply(rows -> null);
    }

    public CompletableFuture<Optional<WebAccount>> findByUsername(String username) {
        return data.queryOne("SELECT * FROM rock_web_accounts WHERE username = :u",
                Map.of("u", username), MAPPER);
    }

    public CompletableFuture<Long> count() {
        return data.queryOne("SELECT COUNT(*) AS c FROM rock_web_accounts", Map.of(),
                row -> row.getLong("c")).thenApply(opt -> opt.orElse(0L));
    }

    public CompletableFuture<Void> touchLogin(UUID id, Instant when) {
        return data.update("UPDATE rock_web_accounts SET last_login = :t WHERE id = :id",
                Map.of("t", when.toEpochMilli(), "id", id.toString())).thenApply(rows -> null);
    }
}
