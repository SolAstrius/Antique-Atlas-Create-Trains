package com.atlasrailways.create;

import com.atlasrailways.AtlasRailways;
import com.atlasrailways.gui.RailPalette;
import com.atlasrailways.model.TrainDatum;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/*
 * Server-side reader for live train positions. Runs on the logical server (dedicated or the
 * integrated server in singleplayer), where Create.RAILWAYS holds every train and ticks them
 * globally. Reads them by reflection - deliberately never touching CreateClient, which is
 * client-only and absent on a dedicated server - and hands back plain TrainDatum for the packet.
 */
public final class CreateServerTrains {
    private CreateServerTrains() {
    }

    private static boolean initialised = false;
    private static boolean ready = false;

    private static Field fRailways;         // Create.RAILWAYS (static)
    private static Field fTrains;           // GlobalRailwayManager.trains
    private static Field fGraphId;          // TrackGraph.id
    private static Field fTrainName;        // Train.name
    private static Field fTrainSpeed;       // Train.speed
    private static Field fTrainGraph;       // Train.graph
    private static Field fTrainCarriages;   // Train.carriages
    private static Field fTpEdge;           // TravellingPoint.edge
    private static Field fTpPosition;       // TravellingPoint.position
    private static Field fTpNode1;          // TravellingPoint.node1
    private static Field fLocDimension;     // TrackNodeLocation.dimension
    private static Method mCarriageLeading; // Carriage.getLeadingPoint()
    private static Method mNodeGetLocation; // TrackNode.getLocation()
    private static Method mEdgeGetLength;   // TrackEdge.getLength()
    private static Method mEdgeGetPosition; // TrackEdge.getPosition(TrackGraph, double)
    private static Method mEdgeGetDirAt;    // TrackEdge.getDirectionAt(double)

    private static synchronized void ensureInit() {
        if (initialised) return;
        initialised = true;
        try {
            Class<?> create = Class.forName("com.simibubi.create.Create");
            Class<?> manager = Class.forName("com.simibubi.create.content.trains.GlobalRailwayManager");
            Class<?> trackGraph = Class.forName("com.simibubi.create.content.trains.graph.TrackGraph");
            Class<?> trackEdge = Class.forName("com.simibubi.create.content.trains.graph.TrackEdge");
            Class<?> trackNode = Class.forName("com.simibubi.create.content.trains.graph.TrackNode");
            Class<?> trackNodeLoc = Class.forName("com.simibubi.create.content.trains.graph.TrackNodeLocation");
            Class<?> train = Class.forName("com.simibubi.create.content.trains.entity.Train");
            Class<?> carriage = Class.forName("com.simibubi.create.content.trains.entity.Carriage");
            Class<?> tp = Class.forName("com.simibubi.create.content.trains.entity.TravellingPoint");

            fRailways = create.getField("RAILWAYS");
            fTrains = manager.getField("trains");
            fGraphId = trackGraph.getField("id");
            fTrainName = train.getField("name");
            fTrainSpeed = train.getField("speed");
            fTrainGraph = train.getField("graph");
            fTrainCarriages = train.getField("carriages");
            fTpEdge = tp.getField("edge");
            fTpPosition = tp.getField("position");
            fTpNode1 = tp.getField("node1");
            fLocDimension = trackNodeLoc.getField("dimension");
            mCarriageLeading = carriage.getMethod("getLeadingPoint");
            mNodeGetLocation = trackNode.getMethod("getLocation");
            mEdgeGetLength = trackEdge.getMethod("getLength");
            mEdgeGetPosition = trackEdge.getMethod("getPosition", trackGraph, double.class);
            mEdgeGetDirAt = trackEdge.getMethod("getDirectionAt", double.class);

            ready = true;
            AtlasRailways.LOGGER.info("Server train tracking connected to Create.RAILWAYS.");
        } catch (Throwable t) {
            ready = false;
            AtlasRailways.LOGGER.info("Server train tracking unavailable. ({})", t.toString());
        }
    }

    public static boolean isAvailable() {
        ensureInit();
        return ready;
    }

    /** Reads every train the server knows about. Returns an empty list if unavailable. */
    @SuppressWarnings("unchecked")
    public static List<TrainDatum> readAll() {
        ensureInit();
        if (!ready) return List.of();
        try {
            Object railways = fRailways.get(null);
            if (railways == null) return List.of();
            Map<UUID, Object> trains = (Map<UUID, Object>) fTrains.get(railways);
            if (trains == null || trains.isEmpty()) return List.of();
            List<TrainDatum> out = new ArrayList<>();
            for (Object train : trains.values()) {
                TrainDatum d = readTrain(train);
                if (d != null) out.add(d);
            }
            return out;
        } catch (Throwable t) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static TrainDatum readTrain(Object train) {
        try {
            Object graph = fTrainGraph.get(train);
            if (graph == null) return null;
            List<Object> carriages = (List<Object>) fTrainCarriages.get(train);
            if (carriages == null || carriages.isEmpty()) return null;

            Object point = mCarriageLeading.invoke(carriages.get(0));
            if (point == null) return null;
            Object edge = fTpEdge.get(point);
            Object node1 = fTpNode1.get(point);
            if (edge == null || node1 == null) return null;

            Object nodeLoc = mNodeGetLocation.invoke(node1);
            Object dimObj = nodeLoc == null ? null : fLocDimension.get(nodeLoc);
            if (!(dimObj instanceof RegistryKey<?> rk)) return null;
            Identifier dimension = rk.getValue();

            double position = fTpPosition.getDouble(point);
            double length = (double) mEdgeGetLength.invoke(edge);
            double t = length <= 1.0e-6 ? 0.0 : Math.max(0.0, Math.min(1.0, position / length));
            Vec3d pos = (Vec3d) mEdgeGetPosition.invoke(edge, graph, t);
            if (pos == null) return null;

            double speed = fTrainSpeed.getDouble(train);
            // getDirectionAt wants the raw distance along the edge, not the normalised t.
            Vec3d dir = (Vec3d) mEdgeGetDirAt.invoke(edge, position);
            double heading = Math.atan2(dir.z, dir.x);
            if (speed < 0) heading += Math.PI;

            Object nameObj = fTrainName.get(train);
            String name = nameObj instanceof Text txt ? txt.getString() : String.valueOf(nameObj);
            int color = RailPalette.colorForNetwork((UUID) fGraphId.get(graph));

            return new TrainDatum(dimension, pos.x, pos.z, (float) heading,
                (float) (Math.abs(speed) * 20.0), color, name);
        } catch (Throwable t) {
            return null;
        }
    }
}
