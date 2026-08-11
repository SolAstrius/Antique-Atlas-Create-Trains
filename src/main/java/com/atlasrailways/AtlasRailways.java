package com.atlasrailways;

import net.minecraft.util.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Shared constants for Atlas Railways.
 *
 * This mod is entirely client-side. It reads Create's railway graph (which the server syncs to
 * every client for train rendering) and paints it onto the Antique Atlas map as a subway-style
 * diagram: one colour per network, a roundel at every station, and lines that follow the real
 * track geometry (curves included) at any zoom level.
 */
public final class AtlasRailways {
    public static final String ID = "atlasrailways";
    public static final Logger LOGGER = LoggerFactory.getLogger("Atlas Railways");

    private AtlasRailways() {
    }

    public static Identifier id(String path) {
        return Identifier.of(ID, path);
    }
}
