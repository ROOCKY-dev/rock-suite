package dev.rock.discord;

import dev.rock.api.annotations.RockInternal;
import dev.rock.api.event.EventBus;
import dev.rock.api.event.EventPriority;
import dev.rock.api.event.Subscription;
import dev.rock.api.events.player.PlayerChatEvent;
import dev.rock.api.events.player.PlayerJoinEvent;
import dev.rock.api.events.player.PlayerLeaveEvent;
import dev.rock.api.lifecycle.LifecycleAware;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Minecraft → Discord chat bridge (DiscordSRV/DiscordIntegration parity).
 * Subscribes to platform events at LAST priority (never sees muted/cancelled
 * chat) and delivers through the rate-limited queue — never the game thread
 * (TRS §13). Discord → Minecraft arrives with the gateway transport (roadmap P2).
 */
@RockInternal
@Singleton
public final class ChatBridgeListener implements LifecycleAware {

    private static final Logger log = LoggerFactory.getLogger(ChatBridgeListener.class);

    private final EventBus eventBus;
    private final DiscordMessageQueue queue;
    private final DiscordSettings settings;
    private final List<Subscription> subscriptions = new ArrayList<>();

    @Inject
    public ChatBridgeListener(EventBus eventBus, DiscordMessageQueue queue, DiscordSettings settings) {
        this.eventBus = eventBus;
        this.queue = queue;
        this.settings = settings;
    }

    @Override
    public void onEnable() {
        String channel = settings.chatBridgeChannel();
        if (channel == null || channel.isBlank()) {
            log.info("No discord.chat-bridge-channel configured; chat bridge disabled");
            return;
        }
        subscriptions.add(eventBus.subscribe(PlayerChatEvent.class, EventPriority.LAST, false, event ->
                queue.enqueue(channel, "**" + event.username() + "**: " + event.message())));
        subscriptions.add(eventBus.subscribe(PlayerJoinEvent.class, EventPriority.LAST, event ->
                queue.enqueue(channel, "➕ **" + event.player().username() + "** joined the server")));
        subscriptions.add(eventBus.subscribe(PlayerLeaveEvent.class, EventPriority.LAST, event ->
                queue.enqueue(channel, "➖ **" + event.player().username() + "** left the server")));
        log.info("Chat bridge active → channel {}", channel);
    }

    @Override
    public void onDisable() {
        subscriptions.forEach(Subscription::close);
        subscriptions.clear();
    }
}
