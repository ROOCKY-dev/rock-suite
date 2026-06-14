package dev.rock.client.hud;

import dev.rock.client.net.RockClientProtocol;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * RFC-001 Tier 1 HUD: a small wallet readout (top-left) and transient toasts
 * (claim entry / balance change), driven entirely by server projections.
 */
public final class WalletHud {

    private static final int ACCENT = 0xFFF5A623;
    private static final int INK = 0xFFE6EDF3;

    private WalletHud() {
    }

    public static void register(RockClientProtocol protocol) {
        HudRenderCallback.EVENT.register((graphics, tickCounter) -> render(graphics, protocol));
    }

    private static void render(GuiGraphics graphics, RockClientProtocol protocol) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options.hideGui) {
            return;
        }
        String balance = protocol.walletBalance();
        if (balance != null) {
            graphics.drawString(mc.font, Component.literal("♦ " + balance), 6, 6, ACCENT, true);
        }
        String toast = protocol.activeToast();
        if (toast != null) {
            int x = (graphics.guiWidth() - mc.font.width(toast)) / 2;
            graphics.drawString(mc.font, Component.literal(toast), x, 20, INK, true);
        }
    }
}
