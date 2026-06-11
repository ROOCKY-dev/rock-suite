package dev.rock.api.domain.bounds;

/** Spatial implementation strategies for claim bounds (DMS). */
public enum BoundsType {
    /** Initial implementation — whole chunks only. */
    CHUNK_BASED,
    /** Future — exact block-level precision. */
    BLOCK_CUBOID,
    /** Future — arbitrary shape. */
    POLYGON
}
