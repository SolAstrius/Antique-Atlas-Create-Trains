package com.atlasrailways.gui;

import com.atlasrailways.AtlasRailways;
import com.mojang.blaze3d.systems.RenderSystem;
import folk.sisby.antique_atlas.gui.BookmarkButton;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/*
 * Side-tab button that shows or hides the live train markers. Styled like the built-in bookmark
 * tabs but tinted green, with a bundled "moving train" icon. Dims when trains are hidden, the same
 * way the railways tab does.
 */
public class TrainToggleButton extends BookmarkButton {
    private static final int GREEN = 0x4E9A4E;
    private static final Identifier ICON = AtlasRailways.id("textures/gui/toggle_livetrain.png");

    public TrainToggleButton() {
        super(Text.literal("Toggle live trains"), ICON, GREEN, null, 16, 16, false, false);
    }

    @Override
    public void drawIcon(DrawContext context, int x, int y) {
        boolean dim = !RailwayOverlay.trainsVisible;
        if (dim) {
            RenderSystem.setShaderColor(0.4f, 0.4f, 0.4f, 0.55f);
            super.drawIcon(context, x, y);
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        } else {
            super.drawIcon(context, x, y);
        }
    }
}
