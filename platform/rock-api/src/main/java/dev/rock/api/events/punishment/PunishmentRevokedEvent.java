package dev.rock.api.events.punishment;

import dev.rock.api.domain.RockPunishment;
import dev.rock.api.event.Event;
import java.util.Objects;

/** Fired after a punishment is revoked. */
public record PunishmentRevokedEvent(RockPunishment punishment) implements Event {

    public PunishmentRevokedEvent {
        Objects.requireNonNull(punishment, "punishment");
    }
}
