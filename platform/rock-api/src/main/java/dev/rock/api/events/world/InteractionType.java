package dev.rock.api.events.world;

/** Kinds of player interaction relevant to protection. */
public enum InteractionType {
    /** Generic block use: buttons, doors, levers. */
    USE_BLOCK,
    /** Opening an inventory-holding block: chests, barrels, machines. */
    OPEN_CONTAINER,
    /** Interacting with an entity: item frames, armor stands, animals. */
    USE_ENTITY
}
