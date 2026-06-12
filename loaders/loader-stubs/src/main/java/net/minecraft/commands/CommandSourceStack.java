package net.minecraft.commands;

import net.minecraft.server.level.ServerPlayer;

/** STUB — compile-time mirror of the command source. */
public abstract class CommandSourceStack implements SharedSuggestionProvider {

    /** The player behind this source, or null for console/command blocks. */
    public abstract ServerPlayer getPlayer();
}
