package com.atlasrailways.net;

import com.atlasrailways.model.LiveTrain;
import com.atlasrailways.model.TrainDatum;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/*
 * Client-side holder for the latest train positions received from the server, grouped by dimension.
 * The overlay reads it each frame. If no packet has arrived recently (server doesn't have this mod),
 * hasRecentData() is false and the overlay falls back to reading nearby trains locally.
 */
public final class LiveTrainStore {
    private LiveTrainStore() {
    }

    private static final long FRESH_MS = 10_000L;

    private static volatile Map<Identifier, List<LiveTrain>> byDimension = Map.of();
    private static volatile long lastUpdate = 0L;

    public static void update(List<TrainDatum> data) {
        Map<Identifier, List<LiveTrain>> next = new HashMap<>();
        for (TrainDatum d : data) {
            next.computeIfAbsent(d.dimension(), k -> new ArrayList<>())
                .add(new LiveTrain(d.x(), d.z(), d.heading(), d.name(), d.speed(), d.color()));
        }
        byDimension = next;
        lastUpdate = System.currentTimeMillis();
    }

    public static List<LiveTrain> get(Identifier dimension) {
        return byDimension.getOrDefault(dimension, List.of());
    }

    public static boolean hasRecentData() {
        return System.currentTimeMillis() - lastUpdate < FRESH_MS;
    }

    public static void clear() {
        byDimension = Map.of();
        lastUpdate = 0L;
    }
}
