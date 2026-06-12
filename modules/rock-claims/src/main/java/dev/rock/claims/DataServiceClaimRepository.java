package dev.rock.claims;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.data.DataService;
import dev.rock.api.data.RowMapper;
import dev.rock.api.domain.ClaimFlag;
import dev.rock.api.domain.ClaimRole;
import dev.rock.api.domain.ClaimType;
import dev.rock.api.domain.RockClaim;
import dev.rock.api.domain.bounds.BoundsType;
import dev.rock.api.domain.bounds.ChunkBounds;
import dev.rock.api.domain.owner.OwnerReference;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Claim storage on the platform DataService. ChunkBounds serialise to the
 * compact "x,z;x,z" format in bounds_data (DMS ClaimBounds contract).
 */
@RockInternal
@Singleton
public final class DataServiceClaimRepository implements ClaimRepository {

    private static final RowMapper<RockClaim> CLAIM_MAPPER = row -> new RockClaim(
            row.getUuid("id"),
            row.getString("display_name"),
            OwnerReference.deserialize(row.getString("owner_ref")),
            ClaimType.valueOf(row.getString("claim_type")),
            ChunkBounds.deserializeChunks(row.getUuid("world_id"), row.getString("bounds_data")),
            row.getInstant("created"),
            row.getInstant("modified"),
            row.getInstant("deleted_at"));

    private final DataService data;

    @Inject
    public DataServiceClaimRepository(DataService data) {
        this.data = data;
    }

    @Override
    public CompletableFuture<Void> save(RockClaim claim) {
        if (!(claim.bounds() instanceof ChunkBounds chunkBounds)) {
            return CompletableFuture.failedFuture(new IllegalArgumentException(
                    "v1 storage supports CHUNK_BASED bounds only, got " + claim.bounds().type()));
        }
        Map<String, Object> params = new HashMap<>();
        params.put("id", claim.id().toString());
        params.put("display_name", claim.displayName());
        params.put("owner_ref", claim.owner().serialize());
        params.put("claim_type", claim.type().name());
        params.put("world_id", claim.bounds().worldId().toString());
        params.put("bounds_type", BoundsType.CHUNK_BASED.name());
        params.put("bounds_data", chunkBounds.serializeChunks());
        params.put("created", claim.created().toEpochMilli());
        params.put("modified", claim.modified().toEpochMilli());
        params.put("deleted_at", claim.deletedAt() == null ? null : claim.deletedAt().toEpochMilli());

        return data.inTransaction(tx -> {
            tx.update("DELETE FROM rock_claims WHERE id = :id", Map.of("id", claim.id().toString()));
            tx.update("""
                    INSERT INTO rock_claims
                        (id, display_name, owner_ref, claim_type, world_id, bounds_type, bounds_data,
                         created, modified, deleted_at)
                    VALUES (:id, :display_name, :owner_ref, :claim_type, :world_id, :bounds_type, :bounds_data,
                            :created, :modified, :deleted_at)
                    """, params);
            return null;
        });
    }

    @Override
    public CompletableFuture<Optional<RockClaim>> findById(UUID id) {
        return data.queryOne("SELECT * FROM rock_claims WHERE id = :id",
                Map.of("id", id.toString()), CLAIM_MAPPER);
    }

    @Override
    public CompletableFuture<List<RockClaim>> findByOwner(OwnerReference owner) {
        return data.query("SELECT * FROM rock_claims WHERE owner_ref = :owner AND deleted_at IS NULL",
                Map.of("owner", owner.serialize()), CLAIM_MAPPER);
    }

    @Override
    public CompletableFuture<List<RockClaim>> findActiveByWorld(UUID worldId) {
        return data.query("SELECT * FROM rock_claims WHERE world_id = :world AND deleted_at IS NULL",
                Map.of("world", worldId.toString()), CLAIM_MAPPER);
    }

    @Override
    public CompletableFuture<List<RockClaim>> findAllActive() {
        return data.query("SELECT * FROM rock_claims WHERE deleted_at IS NULL", Map.of(), CLAIM_MAPPER);
    }

    @Override
    public CompletableFuture<Void> saveMember(UUID claimId, UUID playerId, ClaimRole role) {
        return data.inTransaction(tx -> {
            tx.update("DELETE FROM rock_claim_members WHERE claim_id = :c AND player_id = :p",
                    Map.of("c", claimId.toString(), "p", playerId.toString()));
            tx.update("INSERT INTO rock_claim_members (claim_id, player_id, role) VALUES (:c, :p, :r)",
                    Map.of("c", claimId.toString(), "p", playerId.toString(), "r", role.name()));
            return null;
        });
    }

    @Override
    public CompletableFuture<Void> deleteMember(UUID claimId, UUID playerId) {
        return data.update("DELETE FROM rock_claim_members WHERE claim_id = :c AND player_id = :p",
                Map.of("c", claimId.toString(), "p", playerId.toString())).thenApply(rows -> null);
    }

    @Override
    public CompletableFuture<Map<UUID, ClaimRole>> membersOf(UUID claimId) {
        return data.query("SELECT player_id, role FROM rock_claim_members WHERE claim_id = :c",
                Map.of("c", claimId.toString()),
                row -> Map.entry(row.getUuid("player_id"), ClaimRole.valueOf(row.getString("role"))))
                .thenApply(entries -> {
                    Map<UUID, ClaimRole> members = new HashMap<>();
                    entries.forEach(e -> members.put(e.getKey(), e.getValue()));
                    return members;
                });
    }

    @Override
    public CompletableFuture<Void> saveFlag(UUID claimId, ClaimFlag flag, boolean value) {
        String key = "flag." + flag.name();
        return data.inTransaction(tx -> {
            tx.update("""
                    DELETE FROM rock_metadata
                    WHERE entity_id = :id AND namespace = 'rock.claims' AND meta_key = :key
                    """,
                    Map.of("id", claimId.toString(), "key", key));
            tx.update("""
                    INSERT INTO rock_metadata (entity_id, entity_type, namespace, meta_key, meta_value, last_modified)
                    VALUES (:id, 'CLAIM', 'rock.claims', :key, :value, :now)
                    """,
                    Map.of("id", claimId.toString(), "key", key, "value", Boolean.toString(value),
                            "now", System.currentTimeMillis()));
            return null;
        });
    }

    @Override
    public CompletableFuture<Map<UUID, Map<ClaimFlag, Boolean>>> allFlags() {
        return data.query("""
                SELECT entity_id, meta_key, meta_value FROM rock_metadata
                WHERE namespace = 'rock.claims' AND meta_key LIKE 'flag.%'
                """, Map.of(), row -> new Object[] {
                        row.getUuid("entity_id"),
                        ClaimFlag.valueOf(row.getString("meta_key").substring("flag.".length())),
                        Boolean.parseBoolean(row.getString("meta_value"))})
                .thenApply(rows -> {
                    Map<UUID, Map<ClaimFlag, Boolean>> all = new HashMap<>();
                    for (Object[] row : rows) {
                        all.computeIfAbsent((UUID) row[0], k -> new HashMap<>())
                                .put((ClaimFlag) row[1], (Boolean) row[2]);
                    }
                    return all;
                });
    }

    @Override
    public CompletableFuture<Map<UUID, Map<UUID, ClaimRole>>> allMembers() {
        return data.query("SELECT claim_id, player_id, role FROM rock_claim_members", Map.of(), row ->
                new Object[] {row.getUuid("claim_id"), row.getUuid("player_id"),
                        ClaimRole.valueOf(row.getString("role"))})
                .thenApply(rows -> {
                    Map<UUID, Map<UUID, ClaimRole>> all = new HashMap<>();
                    for (Object[] row : rows) {
                        all.computeIfAbsent((UUID) row[0], k -> new HashMap<>())
                                .put((UUID) row[1], (ClaimRole) row[2]);
                    }
                    return all;
                });
    }
}
