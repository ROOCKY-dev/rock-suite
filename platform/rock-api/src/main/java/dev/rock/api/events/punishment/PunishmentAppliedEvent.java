package dev.rock.api.events.punishment;

import dev.rock.api.domain.RockPunishment;
import dev.rock.api.event.Event;
import java.util.Objects;

/** Fired after a punishment is committed. */
public record PunishmentAppliedEvent(RockPunishment punishment) implements Event {

    public PunishmentAppliedEvent {
        Objects.requireNonNull(punishment, "punishment");
    }
}
