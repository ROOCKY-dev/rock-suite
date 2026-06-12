package net.fabricmc.fabric.api.message.v1;

import net.fabricmc.fabric.api.event.Event;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.PlayerChatMessage;
import net.minecraft.server.level.ServerPlayer;

/** STUB — compile-time mirror of Fabric API server message events. */
public final class ServerMessageEvents {

    public static final Event<AllowChatMessage> ALLOW_CHAT_MESSAGE = null;

    private ServerMessageEvents() {
    }

    @FunctionalInterface
    public interface AllowChatMessage {
        boolean allowChatMessage(PlayerChatMessage message, ServerPlayer sender, ChatType.Bound params);
    }
}
