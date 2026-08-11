package com.atlasrailways.gui;

import java.util.UUID;

/*
 * A small curated palette of muted, saturated ink tones. Each rail network gets a stable colour
 * from it (keyed by UUID), the way transit maps give every line its own colour, while staying
 * legible on Antique Atlas's sepia parchment instead of clashing with it.
 */
public final class RailPalette {
    private RailPalette() {
    }

    // ARGB, fully opaque. Deep, slightly desaturated hues.
    private static final int[] COLORS = {
        0xFF9C3B2E, // brick red
        0xFF2F5A7A, // ink blue
        0xFF3F6B3F, // forest green
        0xFFB07A18, // ochre / mustard
        0xFF6B3F6B, // plum
        0xFF2F6B63, // teal
        0xFFB5651D, // burnt orange
        0xFF4A4A5A, // slate
        0xFF6E6B2F, // olive
        0xFF7A2F4A, // maroon
        0xFF356B8A, // steel blue
        0xFF5A3A22, // umber
    };

    public static int colorForNetwork(UUID id) {
        if (id == null) return COLORS[0];
        // Mix both halves so networks with similar high bits still spread across the palette.
        long h = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        int idx = (int) Math.floorMod(h, COLORS.length);
        return COLORS[idx];
    }
}
