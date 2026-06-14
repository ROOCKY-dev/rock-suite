package dev.rock.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rock.client.hud.WalletHud;
import dev.rock.client.net.RockClientProtocol;
import dev.rock.client.net.RockProtocolPayload;
import dev.rock.client.screens.ClaimMapScreen;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

/**
 * rock-client entrypoint (RFC-001). Registers the rock:protocol channel, the
 * client protocol handler, the wallet HUD, and the claim-map keybind. Everything
 * is optional polish over a vanilla client — no command loses functionality.
 */
public final class RockClientMod implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        PayloadTypeRegistry.playC2S().register(RockProtocolPayload.TYPE, RockProtocolPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(RockProtocolPayload.TYPE, RockProtocolPayload.CODEC);

        RockClientProtocol protocol = new RockClientProtocol();
        protocol.register();
        WalletHud.register(protocol);

        KeyMapping openMap = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.rock.claimmap", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMap.consumeClick()) {
                client.setScreen(new ClaimMapScreen(protocol));
            }
        });
    }
}
