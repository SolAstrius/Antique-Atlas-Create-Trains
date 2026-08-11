package com.atlasrailways;

import com.atlasrailways.create.RailwayCache;
import com.atlasrailways.gui.RailwayOverlay;
import com.atlasrailways.net.LiveTrainStore;
import com.atlasrailways.net.TrainSyncPayload;
import folk.sisby.antique_atlas.gui.AtlasRenderer;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client entrypoint. Registers the rail overlay with Antique Atlas, receives live train packets,
 * and keeps client state tidy on disconnect.
 */
public class AtlasRailwaysClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        AtlasRenderer.registerOverlay(AtlasRailways.id("railways"), new RailwayOverlay());

        // Live train positions streamed from a server that also has this mod.
        ClientPlayNetworking.registerGlobalReceiver(TrainSyncPayload.ID, (payload, context) ->
            context.client().execute(() -> LiveTrainStore.update(payload.trains())));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            RailwayCache.getInstance().clear();
            LiveTrainStore.clear();
        });
        AtlasRailways.LOGGER.info("Atlas Railways: rail overlay registered with Antique Atlas.");
    }
}
