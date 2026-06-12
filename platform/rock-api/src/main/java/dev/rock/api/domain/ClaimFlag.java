package dev.rock.api.domain;

/**
 * Per-claim toggleable rules (Cadmus-style flags). Defaults are the safe
 * choice for a protected area; owners opt in to risk.
 */
public enum ClaimFlag {
    /** Player-vs-player combat inside the claim. */
    PVP(false),
    /** Explosion block damage (creepers, TNT). */
    EXPLOSIONS(false),
    /** Mob/environment block changes (endermen, etc.). */
    MOB_GRIEFING(false),
    /** Machine-controlled fake players may build/interact (modded automation). */
    FAKE_PLAYERS(false),
    /** Fire spread within the claim. */
    FIRE_SPREAD(false);

    private final boolean defaultValue;

    ClaimFlag(boolean defaultValue) {
        this.defaultValue = defaultValue;
    }

    public boolean defaultValue() {
        return defaultValue;
    }
}
