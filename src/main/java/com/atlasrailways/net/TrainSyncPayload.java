package com.atlasrailways.net;

import com.atlasrailways.AtlasRailways;
import com.atlasrailways.model.TrainDatum;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;

import java.util.ArrayList;
import java.util.List;

/*
 * Server -> client packet carrying every train's live position. Registered on both sides; the
 * server sends it a few times a second, the client stores the latest copy for the overlay.
 */
public record TrainSyncPayload(List<TrainDatum> trains) implements CustomPayload {

    public static final CustomPayload.Id<TrainSyncPayload> ID =
        new CustomPayload.Id<>(AtlasRailways.id("train_sync"));

    public static final PacketCodec<PacketByteBuf, TrainSyncPayload> CODEC =
        PacketCodec.of(TrainSyncPayload::write, TrainSyncPayload::read);

    private void write(PacketByteBuf buf) {
        buf.writeVarInt(trains.size());
        for (TrainDatum t : trains) {
            buf.writeIdentifier(t.dimension());
            buf.writeDouble(t.x());
            buf.writeDouble(t.z());
            buf.writeFloat(t.heading());
            buf.writeFloat(t.speed());
            buf.writeInt(t.color());
            buf.writeString(t.name());
        }
    }

    private static TrainSyncPayload read(PacketByteBuf buf) {
        int n = buf.readVarInt();
        List<TrainDatum> list = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            list.add(new TrainDatum(
                buf.readIdentifier(),
                buf.readDouble(),
                buf.readDouble(),
                buf.readFloat(),
                buf.readFloat(),
                buf.readInt(),
                buf.readString()));
        }
        return new TrainSyncPayload(list);
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
