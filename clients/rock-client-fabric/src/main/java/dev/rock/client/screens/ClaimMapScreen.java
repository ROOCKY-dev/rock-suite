package dev.rock.client.screens;

import dev.rock.client.net.RockClientProtocol;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * RFC-001 Tier 1 claim map (read-only first cut): lists the player's claims as
 * projected by the server. Drag-to-claim and flag toggles are the next slice;
 * this establishes the screen + the claims.list request/projection round trip.
 */
public final class ClaimMapScreen extends Screen {

    private final RockClientProtocol protocol;

    public ClaimMapScreen(RockClientProtocol protocol) {
        super(Component.literal("ROCK — Claims"));
        this.protocol = protocol;
    }

    @Override
    protected void init() {
        protocol.requestClaims(); // ask the server; projections fill in as they arrive
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        super.render(graphics, mouseX, mouseY, delta);
        graphics.drawCenteredString(font, title, width / 2, 16, 0xFFF5A623);

        var claims = protocol.claims();
        if (claims.isEmpty()) {
            graphics.drawCenteredString(font, Component.literal("No claims yet — press claim in-world."),
                    width / 2, height / 2, 0xFF8B97A7);
            return;
        }
        int y = 44;
        for (RockClientProtocol.Claim claim : claims) {
            graphics.drawString(font, Component.literal("▣ " + claim.name() + "  (" + claim.type() + ")"),
                    width / 2 - 120, y, 0xFFE6EDF3, false);
            y += 14;
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
