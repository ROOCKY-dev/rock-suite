package dev.rock.api.domain;

/** Member roles within a team, lowest to highest. */
public enum TeamRole {
    MEMBER,
    OFFICER,
    LEADER;

    public boolean atLeast(TeamRole required) {
        return ordinal() >= required.ordinal();
    }
}
