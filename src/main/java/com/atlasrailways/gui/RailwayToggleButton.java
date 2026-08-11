package com.atlasrailways.gui;

import com.atlasrailways.AtlasRailways;
import com.mojang.blaze3d.systems.RenderSystem;
import folk.sisby.antique_atlas.gui.BookmarkButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/*
 * Side-tab button that shows or hides the rail overlay (lines + station markers). Styled like the
 * built-in bookmark tabs but tinted blue, with a bundled locomotive icon (matching the live-trains
 * tab). Dims when the overlay is hidden.
 */
public class RailwayToggleButton extends BookmarkButton {
    private static final int BLUE = 0x4A90D9;
    private static final Identifier ICON = AtlasRailways.id("textures/gui/toggle_train.png");

    public RailwayToggleButton() {
        super(Text.literal("Toggle railways"), ICON, BLUE, null, 16, 16, false, false);
    }

    @Override
    public void drawIcon(DrawContext context, int x, int y) {
        boolean dim = !RailwayOverlay.visible;
        if (dim) {
            RenderSystem.setShaderColor(0.4f, 0.4f, 0.4f, 0.55f);
            super.drawIcon(context, x, y);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        } else {
            super.drawIcon(context, x, y);
        }
    }
}
