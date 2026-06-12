package dev.rock.claims;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.domain.ClaimFlag;
import dev.rock.api.domain.ClaimRole;
import dev.rock.api.domain.RockClaim;
import dev.rock.api.event.EventBus;
import dev.rock.api.event.EventPriority;
import dev.rock.api.event.Subscription;
import dev.rock.api.events.world.BlockChangeEvent;
import dev.rock.api.events.world.PlayerInteractEvent;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.services.ClaimService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Claim protection enforcement on the world-interaction event layer (K1).
 * Runs at EARLY priority on the tick thread against the in-memory claim
 * index — no I/O, within the TRS §3 event budget.
 *
 * <p>Policy: inside an active claim — block changes need BUILD, containers
 * need CONTAINER, other interactions need ACCESS. Actor-less changes (mobs,
 * environment) are governed by the MOB_GRIEFING flag; fake players (machines)
 * by the FAKE_PLAYERS flag (both default deny — ClaimFlag defaults).
 */
@RockInternal
@Singleton
public final class ClaimProtectionListener implements LifecycleAware {

    private final ClaimService claims;
    private final EventBus eventBus;
    private final List<Subscription> subscriptions = new ArrayList<>();

    @Inject
    public ClaimProtectionListener(ClaimService claims, EventBus eventBus) {
        this.claims = claims;
        this.eventBus = eventBus;
    }

    @Override
    public void onEnable() {
        subscriptions.add(eventBus.subscribe(BlockChangeEvent.class, EventPriority.EARLY, this::onBlockChange));
        subscriptions.add(eventBus.subscribe(PlayerInteractEvent.class, EventPriority.EARLY, this::onInteract));
    }

    @Override
    public void onDisable() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
    }

    private void onBlockChange(BlockChangeEvent event) {
        Optional<RockClaim> claim = claims.claimAtCached(event.worldId(), event.x(), event.y(), event.z());
        if (claim.isEmpty()) {
            return;
        }
        if (event.actor() == null) {
            if (!claims.flag(claim.get(), ClaimFlag.MOB_GRIEFING)) {
                event.cancel();
            }
            return;
        }
        if (event.fakePlayer()) {
            if (!claims.flag(claim.get(), ClaimFlag.FAKE_PLAYERS)) {
                event.cancel();
            }
            return;
        }
        if (!hasRole(claim.get(), event.actor(), ClaimRole.BUILD)) {
            event.cancel();
        }
    }

    private void onInteract(PlayerInteractEvent event) {
        Optional<RockClaim> claim = claims.claimAtCached(event.worldId(), event.x(), event.y(), event.z());
        if (claim.isEmpty()) {
            return;
        }
        if (event.fakePlayer()) {
            if (!claims.flag(claim.get(), ClaimFlag.FAKE_PLAYERS)) {
                event.cancel();
            }
            return;
        }
        ClaimRole required = switch (event.type()) {
            case OPEN_CONTAINER -> ClaimRole.CONTAINER;
            case USE_BLOCK, USE_ENTITY -> ClaimRole.ACCESS;
        };
        if (!hasRole(claim.get(), event.actor(), required)) {
            event.cancel();
        }
    }

    private boolean hasRole(RockClaim claim, java.util.UUID playerId, ClaimRole required) {
        return claims.effectiveRole(claim, playerId)
                .map(role -> role.atLeast(required))
                .orElse(false);
    }
}
