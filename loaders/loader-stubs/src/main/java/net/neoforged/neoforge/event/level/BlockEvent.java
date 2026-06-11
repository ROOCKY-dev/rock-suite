package net.neoforged.neoforge.event.level;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

/** STUB — compile-time mirror of NeoForge block events. */
public abstract class BlockEvent extends Event {

    public abstract Level getLevel();

    public abstract BlockPos getPos();

    public abstract BlockState getState();

    public abstract static class BreakEvent extends BlockEvent implements ICancellableEvent {
        public abstract Player getPlayer();
    }

    public abstract static class EntityPlaceEvent extends BlockEvent implements ICancellableEvent {
        /** The player when placed by one; null otherwise (stub simplification). */
        public abstract Player getPlacer();

        public abstract BlockState getPlacedBlock();
    }
}
