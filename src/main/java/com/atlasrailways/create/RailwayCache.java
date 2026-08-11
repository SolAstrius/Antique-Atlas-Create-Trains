package com.atlasrailways.create;

import com.atlasrailways.model.RailwaySnapshot;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/*
 * Holds the baked RailwaySnapshot per dimension and decides when to rebuild it.
 *
 * Rebuilds happen only when Create signals a graph change (its version counter), when a different
 * dimension is viewed, or after a short idle interval (to catch station renames, which don't bump
 * the graph version). The overlay reads whatever is cached without forcing a rebuild mid-frame, so
 * scrolling and zooming stay allocation-free.
 */
public final class RailwayCache {
    private static final RailwayCache INSTANCE = new RailwayCache();

    /** Fallback rebuild interval, in milliseconds, to pick up changes that don't bump the version. */
    private static final long REFRESH_INTERVAL_MS = 5_000L;

    private final Map<Identifier, RailwaySnapshot> byDimension = new HashMap<>();
    private int lastVersion = Integer.MIN_VALUE;
    private long lastBuildTime = 0L;

    private RailwayCache() {
    }

    public static RailwayCache getInstance() {
        return INSTANCE;
    }

    /** Returns the snapshot for {@code dimension}, rebuilding lazily when stale. Never null. */
    public RailwaySnapshot get(Identifier dimension) {
        if (!CreateRailways.isAvailable()) return RailwaySnapshot.EMPTY;

        int version = CreateRailways.version();
        long now = System.currentTimeMillis();
        boolean versionChanged = version != lastVersion;
        boolean stale = now - lastBuildTime > REFRESH_INTERVAL_MS;

        if (versionChanged || stale) {
            byDimension.clear();
            lastVersion = version;
            lastBuildTime = now;
        }

        RailwaySnapshot cached = byDimension.get(dimension);
        if (cached != null) return cached;

        RailwaySnapshot built = CreateRailways.buildSnapshot(dimension);
        byDimension.put(dimension, built);
        return built;
    }

    /** Drops all cached data (e.g. on disconnect). */
    public void clear() {
        byDimension.clear();
        lastVersion = Integer.MIN_VALUE;
        lastBuildTime = 0L;
    }
}
