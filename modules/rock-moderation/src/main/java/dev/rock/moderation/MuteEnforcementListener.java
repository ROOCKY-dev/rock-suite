package dev.rock.moderation;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.domain.PunishmentType;
import dev.rock.api.event.EventBus;
import dev.rock.api.event.EventPriority;
import dev.rock.api.event.Subscription;
import dev.rock.api.events.player.PlayerChatEvent;
import dev.rock.api.lifecycle.LifecycleAware;
import dev.rock.api.services.PunishmentService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Cancels chat from muted players at FIRST priority — before filters, before
 * the Discord bridge, before anything (TRS §8 ordering contract).
 */
@RockInternal
@Singleton
public final class MuteEnforcementListener implements LifecycleAware {

    private final EventBus eventBus;
    private final PunishmentService punishments;
    private Subscription subscription;

    @Inject
    public MuteEnforcementListener(EventBus eventBus, PunishmentService punishments) {
        this.eventBus = eventBus;
        this.punishments = punishments;
    }

    @Override
    public void onEnable() {
        subscription = eventBus.subscribe(PlayerChatEvent.class, EventPriority.FIRST, event ->
                punishments.activeCached(event.actor(), PunishmentType.MUTE)
                        .ifPresent(mute -> event.cancel()));
    }

    @Override
    public void onDisable() {
        if (subscription != null) {
            subscription.close();
        }
    }
}
