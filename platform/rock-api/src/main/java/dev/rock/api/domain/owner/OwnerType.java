package dev.rock.api.domain.owner;

/** Discriminator for {@link OwnerReference} implementations. */
public enum OwnerType {
    PLAYER,
    GROUP,
    CLAIM,
    SYSTEM
}
