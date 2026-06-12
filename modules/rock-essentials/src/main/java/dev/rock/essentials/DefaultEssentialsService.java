package dev.rock.essentials;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.data.DataService;
import dev.rock.api.data.RowMapper;
import dev.rock.api.domain.RockLocation;
import dev.rock.api.service.ServiceRegistry;
import dev.rock.api.services.EssentialsService;
import dev.rock.api.services.PermissionService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Homes/warps/TPA engine (FTB Essentials / EssentialCommands answer). Home
 * limits come from the permission option {@code rock.essentials.homes.max} —
 * one typed-limit system across the platform.
 */
@RockInternal
@Singleton
public final class DefaultEssentialsService implements EssentialsService {

    static final int DEFAULT_HOME_LIMIT = 3;
    static final Duration TPA_WINDOW = Duration.ofSeconds(60);

    private static final RowMapper<RockLocation> LOCATION_MAPPER = row -> new RockLocation(
            row.getUuid("world_id"), row.getDouble("x"), row.getDouble("y"), row.getDouble("z"),
            row.getDouble("yaw").floatValue(), row.getDouble("pitch").floatValue());

    private record TpaRequest(UUID requester, Instant expiresAt) {
        boolean expired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final DataService data;
    private final ServiceRegistry services;
    private final Map<UUID, TpaRequest> tpaByTarget = new ConcurrentHashMap<>();

    @Inject
    public DefaultEssentialsService(DataService data, ServiceRegistry services) {
        this.data = data;
        this.services = services;
    }

    private int homeLimit(UUID playerId) {
        return services.find(PermissionService.class)
                .map(permissions -> permissions.intOption(playerId, HOME_LIMIT_OPTION).orElse(DEFAULT_HOME_LIMIT))
                .orElse(DEFAULT_HOME_LIMIT);
    }

    private static Map<String, Object> locationParams(RockLocation location) {
        Map<String, Object> params = new HashMap<>();
        params.put("world", location.worldId().toString());
        params.put("x", location.x());
        params.put("y", location.y());
        params.put("z", location.z());
        params.put("yaw", (double) location.yaw());
        params.put("pitch", (double) location.pitch());
        return params;
    }

    // --- Homes ---------------------------------------------------------------

    @Override
    public CompletableFuture<Void> setHome(UUID playerId, String name, RockLocation location) {
        int limit = homeLimit(playerId);
        return data.inTransaction(tx -> {
            boolean replacing = tx.queryOne(
                    "SELECT name FROM rock_homes WHERE player_id = :p AND name = :n",
                    Map.of("p", playerId.toString(), "n", name), row -> row.getString("name")).isPresent();
            long count = tx.queryOne("SELECT COUNT(*) AS c FROM rock_homes WHERE player_id = :p",
                    Map.of("p", playerId.toString()), row -> row.getLong("c")).orElse(0L);
            if (!replacing && count >= limit) {
                throw new IllegalStateException(
                        "Home limit reached (" + limit + ") — raise " + HOME_LIMIT_OPTION + " to allow more");
            }
            tx.update("DELETE FROM rock_homes WHERE player_id = :p AND name = :n",
                    Map.of("p", playerId.toString(), "n", name));
            Map<String, Object> params = locationParams(location);
            params.put("p", playerId.toString());
            params.put("n", name);
            tx.update("""
                    INSERT INTO rock_homes (player_id, name, world_id, x, y, z, yaw, pitch)
                    VALUES (:p, :n, :world, :x, :y, :z, :yaw, :pitch)
                    """, params);
            return null;
        });
    }

    @Override
    public CompletableFuture<Optional<RockLocation>> home(UUID playerId, String name) {
        return data.queryOne("SELECT * FROM rock_homes WHERE player_id = :p AND name = :n",
                Map.of("p", playerId.toString(), "n", name), LOCATION_MAPPER);
    }

    @Override
    public CompletableFuture<Void> deleteHome(UUID playerId, String name) {
        return data.update("DELETE FROM rock_homes WHERE player_id = :p AND name = :n",
                Map.of("p", playerId.toString(), "n", name)).thenApply(rows -> null);
    }

    @Override
    public CompletableFuture<List<String>> homes(UUID playerId) {
        return data.query("SELECT name FROM rock_homes WHERE player_id = :p ORDER BY name",
                Map.of("p", playerId.toString()), row -> row.getString("name"));
    }

    // --- Warps ---------------------------------------------------------------

    @Override
    public CompletableFuture<Void> setWarp(String name, RockLocation location, UUID createdBy) {
        return data.inTransaction(tx -> {
            tx.update("DELETE FROM rock_warps WHERE name = :n", Map.of("n", name));
            Map<String, Object> params = locationParams(location);
            params.put("n", name);
            params.put("by", createdBy.toString());
            tx.update("""
                    INSERT INTO rock_warps (name, world_id, x, y, z, yaw, pitch, created_by)
                    VALUES (:n, :world, :x, :y, :z, :yaw, :pitch, :by)
                    """, params);
            return null;
        });
    }

    @Override
    public CompletableFuture<Optional<RockLocation>> warp(String name) {
        return data.queryOne("SELECT * FROM rock_warps WHERE name = :n", Map.of("n", name), LOCATION_MAPPER);
    }

    @Override
    public CompletableFuture<Void> deleteWarp(String name) {
        return data.update("DELETE FROM rock_warps WHERE name = :n", Map.of("n", name))
                .thenApply(rows -> null);
    }

    @Override
    public CompletableFuture<List<String>> warps() {
        return data.query("SELECT name FROM rock_warps ORDER BY name", Map.of(), row -> row.getString("name"));
    }

    // --- TPA -------------------------------------------------------------------

    @Override
    public void tpa(UUID requester, UUID target) {
        tpaByTarget.put(target, new TpaRequest(requester, Instant.now().plus(TPA_WINDOW)));
    }

    @Override
    public Optional<UUID> tpaccept(UUID target) {
        TpaRequest request = tpaByTarget.remove(target);
        if (request == null || request.expired()) {
            return Optional.empty();
        }
        return Optional.of(request.requester());
    }

    @Override
    public Optional<UUID> tpdeny(UUID target) {
        TpaRequest request = tpaByTarget.remove(target);
        return request == null || request.expired() ? Optional.empty() : Optional.of(request.requester());
    }
}
