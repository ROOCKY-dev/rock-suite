package dev.rock.discord;

import dev.rock.api.config.RockConfig;

/**
 * Parsed rock-discord.toml settings.
 *
 * @param token            bot token, blank = delivery disabled (no-op gateway)
 * @param sendIntervalMs   queue pacing between sends
 * @param chatBridgeChannel channel id for the MC→Discord chat bridge; blank = disabled
 */
public record DiscordSettings(String token, long sendIntervalMs, String chatBridgeChannel) {

    public static DiscordSettings fromConfig(RockConfig config) {
        return new DiscordSettings(
                config.getString("discord.token", ""),
                config.getLong("discord.send-interval-ms", 250),
                config.getString("discord.chat-bridge-channel", ""));
    }
}
