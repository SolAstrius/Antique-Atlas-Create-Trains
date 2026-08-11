package com.atlasrailways;

import com.atlasrailways.create.CreateServerTrains;
import com.atlasrailways.model.TrainDatum;
import com.atlasrailways.net.TrainSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.List;

/*
 * Runs on both sides. Registers the train-sync packet, and (on the logical server) streams every
 * train's live position to players a few times a second so distant trains keep moving on the map -
 * something a client-only mod can't do, since Create only ticks trains server-side.
 */
public class AtlasRailwaysCommon implements ModInitializer {
    /** Ticks between train-position broadcasts (20 ticks = 1s), so ~7 updates/second. */
    private static final int SEND_INTERVAL = 3;

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playS2C().register(TrainSyncPayload.ID, TrainSyncPayload.CODEC);

        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % SEND_INTERVAL != 0) return;
            if (!CreateServerTrains.isAvailable()) return;

            List<TrainDatum> data = CreateServerTrains.readAll();
            TrainSyncPayload payload = new TrainSyncPayload(data);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (ServerPlayNetworking.canSend(player, TrainSyncPayload.ID)) {
                    ServerPlayNetworking.send(player, payload);
                }
            }
        });
    }
}
