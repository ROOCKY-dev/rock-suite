package dev.rock.claims;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.data.DataService;
import dev.rock.api.data.RowMapper;
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
}
