package net.fabricmc.fabric.api.event.player;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/** STUB — compile-time mirror of Fabric API block-break events. */
public final class PlayerBlockBreakEvents {

    public static final Event<Before> BEFORE = null;

    private PlayerBlockBreakEvents() {
    }

    @FunctionalInterface
    public interface Before {
        boolean beforeBlockBreak(Level world, Player player, BlockPos pos, BlockState state, BlockEntity blockEntity);
    }
}
