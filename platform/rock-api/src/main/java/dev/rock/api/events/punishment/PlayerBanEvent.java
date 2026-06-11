package dev.rock.api.events.punishment;

import dev.rock.api.domain.RockPunishment;
import dev.rock.api.event.AbstractCancellable;
import java.util.Objects;

/** Pre-action ban event; cancellable (TRS §8). */
public final class PlayerBanEvent extends AbstractCancellable {

    private final RockPunishment punishment;

    public PlayerBanEvent(RockPunishment punishment) {
        this.punishment = Objects.requireNonNull(punishment, "punishment");
    }

    public RockPunishment punishment() {
        return punishment;
    }
}
