package dev.rock.api.domain.bounds;

import java.util.UUID;

/**
 * Physical region of a claim, decoupled from RockClaim so spatial
 * implementations can evolve without changing the claim model (DMS).
 */
public interface ClaimBounds {

    UUID worldId();

    BoundsType type();

    boolean contains(int x, int y, int z);

    boolean overlaps(ClaimBounds other);

    long volume();
}
