package com.atlasrailways.create;

import com.atlasrailways.AtlasRailways;
import com.atlasrailways.gui.RailPalette;
import com.atlasrailways.model.LiveTrain;
import com.atlasrailways.model.RailwaySnapshot;
import net.minecraft.registry.RegistryKey;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.Vec3i;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/*
 * Reflection bridge to Create's client-side railway data.
 *
 * Create is NeoForge (loaded via Sinytra Connector); this mod is Fabric/Yarn. Compiling directly
 * against Create across that boundary isn't practical, but at runtime Create's own class/field/
 * method names stay intact and only its Minecraft references get remapped to intermediary (same as
 * this mod's loom-remapped code), so any Minecraft object it returns casts cleanly to a Yarn type.
 *
 * Reflection is kept in this class and only runs when the cache is (re)built, never per frame.
 */
public final class CreateRailways {
    private CreateRailways() {
    }

    private static boolean initialised = false;
    private static boolean ready = false;

    // Create handles ------------------------------------------------------
    private static Field fRailways;            // CreateClient.RAILWAYS (static)
    private static Field fTrackNetworks;       // GlobalRailwayManager.trackNetworks
    private static Field fVersion;             // GlobalRailwayManager.version
    private static Field fGraphId;             // TrackGraph.id
    private static Field fEdgeNode1;           // TrackEdge.node1
    private static Field fEdgeNode2;           // TrackEdge.node2
    private static Field fLocDimension;        // TrackNodeLocation.dimension
    private static Field fStationName;         // GlobalStation.name
    private static Object stationType;         // EdgePointType.STATION (static)

    private static Method mGetNodes;           // TrackGraph.getNodes()
    private static Method mLocateNode;         // TrackGraph.locateNode(TrackNodeLocation)
    private static Method mGetConnectionsFrom; // TrackGraph.getConnectionsFrom(TrackNode)
    private static Method mGetPoints;          // TrackGraph.getPoints(EdgePointType)
    private static Method mNodeGetLocation;    // TrackNode.getLocation()
    private static Method mEdgeIsTurn;         // TrackEdge.isTurn()
    private static Method mEdgeGetPosition;    // TrackEdge.getPosition(TrackGraph, double)
    private static Method mStationPos;         // GlobalStation.getBlockEntityPos()
    private static Method mStationDim;         // GlobalStation.getBlockEntityDimension()

    // Optional: locate a station's point *on the track* rather than at its block.
    private static Field fEdgeLocation;        // TrackEdgePoint.edgeLocation (Couple<TrackNodeLocation>)
    private static Method mGetLocationOn;      // TrackEdgePoint.getLocationOn(TrackEdge)
    private static Method mEdgeGetLength;      // TrackEdge.getLength()
    private static Method mCoupleFirst;        // catnip Couple.getFirst()
    private static Method mCoupleSecond;       // catnip Couple.getSecond()

    // Optional: live train positions (read every frame, not cached).
    private static boolean trainsReady = false;
    private static Field fTrains;              // GlobalRailwayManager.trains
    private static Field fTrainName;           // Train.name (Component)
    private static Field fTrainSpeed;          // Train.speed
    private static Field fTrainGraph;          // Train.graph
    private static Field fTrainCarriages;      // Train.carriages
    private static Method mCarriageLeading;    // Carriage.getLeadingPoint()
    private static Field fTpEdge;              // TravellingPoint.edge
    private static Field fTpPosition;          // TravellingPoint.position
    private static Field fTpNode1;             // TravellingPoint.node1
    private static Method mEdgeGetDirAt;       // TrackEdge.getDirectionAt(double)

    /** Number of samples used to trace a curved (bezier) track edge. */
    private static final int CURVE_SAMPLES = 8;

    private static synchronized void ensureInit() {
        if (initialised) return;
        initialised = true;
        try {
            Class<?> createClient = Class.forName("com.simibubi.create.CreateClient");
            Class<?> manager = Class.forName("com.simibubi.create.content.trains.GlobalRailwayManager");
            Class<?> trackGraph = Class.forName("com.simibubi.create.content.trains.graph.TrackGraph");
            Class<?> trackEdge = Class.forName("com.simibubi.create.content.trains.graph.TrackEdge");
            Class<?> trackNode = Class.forName("com.simibubi.create.content.trains.graph.TrackNode");
            Class<?> trackNodeLoc = Class.forName("com.simibubi.create.content.trains.graph.TrackNodeLocation");
            Class<?> edgePointType = Class.forName("com.simibubi.create.content.trains.graph.EdgePointType");
            Class<?> globalStation = Class.forName("com.simibubi.create.content.trains.station.GlobalStation");

            fRailways = createClient.getField("RAILWAYS");
            fTrackNetworks = manager.getField("trackNetworks");
            fVersion = manager.getField("version");
            fGraphId = trackGraph.getField("id");
            fEdgeNode1 = trackEdge.getField("node1");
            fEdgeNode2 = trackEdge.getField("node2");
            fLocDimension = trackNodeLoc.getField("dimension");
            fStationName = globalStation.getField("name");
            stationType = edgePointType.getField("STATION").get(null);

            mGetNodes = trackGraph.getMethod("getNodes");
            mLocateNode = trackGraph.getMethod("locateNode", trackNodeLoc);
            mGetConnectionsFrom = trackGraph.getMethod("getConnectionsFrom", trackNode);
            mGetPoints = trackGraph.getMethod("getPoints", edgePointType);
            mNodeGetLocation = trackNode.getMethod("getLocation");
            mEdgeIsTurn = trackEdge.getMethod("isTurn");
            mEdgeGetPosition = trackEdge.getMethod("getPosition", trackGraph, double.class);
            mStationPos = globalStation.getMethod("getBlockEntityPos");
            mStationDim = globalStation.getMethod("getBlockEntityDimension");

            // Optional: on-track position. If any of this is missing the code falls back to the
            // station block position, so it must not disable the overlay.
            try {
                Class<?> couple = Class.forName("net.createmod.catnip.data.Couple");
                fEdgeLocation = globalStation.getField("edgeLocation");
                mGetLocationOn = globalStation.getMethod("getLocationOn", trackEdge);
                mEdgeGetLength = trackEdge.getMethod("getLength");
                mCoupleFirst = couple.getMethod("getFirst");
                mCoupleSecond = couple.getMethod("getSecond");
            } catch (Throwable t) {
                fEdgeLocation = null;
                AtlasRailways.LOGGER.info("On-track station positioning unavailable; using station block position. ({})", t.toString());
            }

            // Optional: live train tracking. Failure here only disables trains, not the rail overlay.
            try {
                Class<?> train = Class.forName("com.simibubi.create.content.trains.entity.Train");
                Class<?> carriage = Class.forName("com.simibubi.create.content.trains.entity.Carriage");
                Class<?> travellingPoint = Class.forName("com.simibubi.create.content.trains.entity.TravellingPoint");
                fTrains = manager.getField("trains");
                fTrainName = train.getField("name");
                fTrainSpeed = train.getField("speed");
                fTrainGraph = train.getField("graph");
                fTrainCarriages = train.getField("carriages");
                mCarriageLeading = carriage.getMethod("getLeadingPoint");
                fTpEdge = travellingPoint.getField("edge");
                fTpPosition = travellingPoint.getField("position");
                fTpNode1 = travellingPoint.getField("node1");
                mEdgeGetDirAt = trackEdge.getMethod("getDirectionAt", double.class);
                trainsReady = true;
            } catch (Throwable t) {
                trainsReady = false;
                AtlasRailways.LOGGER.info("Live train tracking unavailable. ({})", t.toString());
            }

            ready = true;
            AtlasRailways.LOGGER.info("Connected to Create's railway graph via reflection.");
        } catch (Throwable t) {
            ready = false;
            AtlasRailways.LOGGER.warn("Create railway data is unavailable; rail overlay disabled. ({})", t.toString());
        }
    }

    public static boolean isAvailable() {
        ensureInit();
        return ready;
    }

    public static boolean trainsAvailable() {
        ensureInit();
        return ready && trainsReady;
    }

    /**
     * Reads every train's live position in the given dimension. Unlike the graph, trains move, so
     * this is called every frame and is NOT cached. The work is bounded by the (usually small)
     * number of trains.
     */
    @SuppressWarnings("unchecked")
    public static List<LiveTrain> readTrains(Identifier dimension) {
        ensureInit();
        if (!ready || !trainsReady) return List.of();
        try {
            Object railways = fRailways.get(null);
            if (railways == null) return List.of();
            Map<UUID, Object> trains = (Map<UUID, Object>) fTrains.get(railways);
            if (trains == null || trains.isEmpty()) return List.of();
            List<LiveTrain> out = new ArrayList<>();
            for (Object train : trains.values()) {
                LiveTrain lt = readTrain(train, dimension);
                if (lt != null) out.add(lt);
            }
            return out;
        } catch (Throwable t) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private static LiveTrain readTrain(Object train, Identifier dimension) {
        try {
            Object graph = fTrainGraph.get(train);
            if (graph == null) return null;
            List<Object> carriages = (List<Object>) fTrainCarriages.get(train);
            if (carriages == null || carriages.isEmpty()) return null;

            // The leading travelling point of the first carriage: its edge + distance = world spot.
            Object point = mCarriageLeading.invoke(carriages.get(0));
            if (point == null) return null;
            Object edge = fTpEdge.get(point);
            Object node1 = fTpNode1.get(point);
            if (edge == null || node1 == null) return null;

            // Dimension filter, from the node the train sits on.
            Object nodeLoc = mNodeGetLocation.invoke(node1);
            Object dimObj = nodeLoc == null ? null : fLocDimension.get(nodeLoc);
            if (dimObj instanceof RegistryKey<?> rk && !rk.getValue().equals(dimension)) return null;

            double position = fTpPosition.getDouble(point);
            double length = (double) mEdgeGetLength.invoke(edge);
            double t = length <= 1.0e-6 ? 0.0 : Math.max(0.0, Math.min(1.0, position / length));
            Vec3d pos = (Vec3d) mEdgeGetPosition.invoke(edge, graph, t);
            if (pos == null) return null;

            double speed = fTrainSpeed.getDouble(train);
            // getDirectionAt expects the raw distance along the edge (it divides by length itself),
            // NOT the normalised t used by getPosition. Passing t makes it snap; pass position.
            Vec3d dir = (Vec3d) mEdgeGetDirAt.invoke(edge, position);
            double heading = Math.atan2(dir.z, dir.x);
            if (speed < 0) heading += Math.PI; // travelling the other way along the edge

            Object nameObj = fTrainName.get(train);
            String name = nameObj instanceof Text txt ? txt.getString() : String.valueOf(nameObj);
            int color = RailPalette.colorForNetwork((UUID) fGraphId.get(graph));

            return new LiveTrain(pos.x, pos.z, heading, name, Math.abs(speed) * 20.0, color);
        } catch (Throwable t) {
            return null;
        }
    }

    /** A monotonically increasing counter Create bumps whenever any graph changes client-side. */
    public static int version() {
        ensureInit();
        if (!ready) return -1;
        try {
            Object railways = fRailways.get(null);
            return railways == null ? -1 : fVersion.getInt(railways);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Builds a fresh snapshot of every rail network in the given dimension. Runs off the render
     * loop (on a cache miss), not per frame.
     */
    @SuppressWarnings("unchecked")
    public static RailwaySnapshot buildSnapshot(Identifier dimension) {
        ensureInit();
        if (!ready) return RailwaySnapshot.EMPTY;
        try {
            Object railways = fRailways.get(null);
            if (railways == null) return RailwaySnapshot.EMPTY;
            Map<UUID, Object> networks = (Map<UUID, Object>) fTrackNetworks.get(railways);
            if (networks == null || networks.isEmpty()) return RailwaySnapshot.EMPTY;

            List<RailwaySnapshot.RailLine> lines = new ArrayList<>();
            for (Map.Entry<UUID, Object> netEntry : networks.entrySet()) {
                Object graph = netEntry.getValue();
                if (graph == null) continue;

                List<RailwaySnapshot.Station> stations = collectStations(graph, dimension);
                List<float[]> polylines = collectEdges(graph, dimension);
                if (stations.isEmpty() && polylines.isEmpty()) continue;

                UUID id = (UUID) fGraphId.get(graph);
                int color = RailPalette.colorForNetwork(id);
                lines.add(new RailwaySnapshot.RailLine(color, polylines, stations));
            }
            return lines.isEmpty() ? RailwaySnapshot.EMPTY : new RailwaySnapshot(lines);
        } catch (Throwable t) {
            AtlasRailways.LOGGER.warn("Failed to read Create railway graph: {}", t.toString());
            return RailwaySnapshot.EMPTY;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<RailwaySnapshot.Station> collectStations(Object graph, Identifier dimension) throws Exception {
        List<RailwaySnapshot.Station> out = new ArrayList<>();
        Collection<Object> points = (Collection<Object>) mGetPoints.invoke(graph, stationType);
        if (points == null) return out;
        for (Object station : points) {
            if (station == null) continue;
            // Filter by the dimension of the track the station sits on. The station's own
            // getBlockEntityDimension() isn't synced to the client (it reads null), so we use the
            // edge-node dimension - the same field that keeps the rails in the right dimension.
            Identifier stDim = stationDimension(station);
            if (stDim != null) {
                if (!stDim.equals(dimension)) continue;
            } else {
                // No track dimension available: fall back to the block dimension, strictly.
                RegistryKey<?> dim = (RegistryKey<?>) mStationDim.invoke(station);
                if (dim == null || !dim.getValue().equals(dimension)) continue;
            }
            Object nameObj = fStationName.get(station);
            String name = nameObj == null ? "" : nameObj.toString();

            // Prefer the point on the track the station targets; fall back to the station block.
            double[] onTrack = trackPosition(graph, station);
            if (onTrack != null) {
                out.add(new RailwaySnapshot.Station(onTrack[0], onTrack[1], name));
            } else {
                BlockPos pos = (BlockPos) mStationPos.invoke(station);
                if (pos == null) continue;
                out.add(new RailwaySnapshot.Station(pos.getX() + 0.5, pos.getZ() + 0.5, name));
            }
        }
        return out;
    }

    /**
     * The dimension of the track a station sits on, or {@code null} if it can't be read. Taken from
     * the station's edge nodes ({@code TrackNodeLocation.dimension}) - the reliable client-side
     * source, since the station's own block dimension isn't synced.
     */
    private static Identifier stationDimension(Object station) {
        if (fEdgeLocation == null || mCoupleFirst == null) return null;
        try {
            Object edgeLocation = fEdgeLocation.get(station);
            if (edgeLocation == null) return null;
            Object loc = mCoupleFirst.invoke(edgeLocation);
            if (loc == null) return null;
            Object dimObj = fLocDimension.get(loc);
            return dimObj instanceof RegistryKey<?> rk ? rk.getValue() : null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * The station's world position on the track it targets, as {@code [x, z]}, or {@code null} to
     * fall back to the station block. A station is a track edge point: locate its edge, read how far
     * along it sits ({@code getLocationOn}), then sample that point on the edge ({@code getPosition}),
     * which also follows curves.
     */
    @SuppressWarnings("unchecked")
    private static double[] trackPosition(Object graph, Object station) {
        if (fEdgeLocation == null || mGetLocationOn == null || mEdgeGetLength == null
            || mCoupleFirst == null || mCoupleSecond == null) return null;
        try {
            Object edgeLocation = fEdgeLocation.get(station);
            if (edgeLocation == null) return null;
            Object loc1 = mCoupleFirst.invoke(edgeLocation);
            Object loc2 = mCoupleSecond.invoke(edgeLocation);
            Object node1 = mLocateNode.invoke(graph, loc1);
            Object node2 = mLocateNode.invoke(graph, loc2);
            if (node1 == null || node2 == null) return null;

            Object edge = edgeBetween(graph, node1, node2);
            if (edge == null) return null;

            double distance = (double) mGetLocationOn.invoke(station, edge);
            double length = (double) mEdgeGetLength.invoke(edge);
            double t = length <= 1.0e-6 ? 0.0 : Math.max(0.0, Math.min(1.0, distance / length));
            Vec3d v = (Vec3d) mEdgeGetPosition.invoke(edge, graph, t);
            return v == null ? null : new double[] { v.x, v.z };
        } catch (Throwable t) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Object edgeBetween(Object graph, Object node1, Object node2) throws Exception {
        Map<Object, Object> from1 = (Map<Object, Object>) mGetConnectionsFrom.invoke(graph, node1);
        Object edge = from1 == null ? null : from1.get(node2);
        if (edge != null) return edge;
        Map<Object, Object> from2 = (Map<Object, Object>) mGetConnectionsFrom.invoke(graph, node2);
        return from2 == null ? null : from2.get(node1);
    }

    @SuppressWarnings("unchecked")
    private static List<float[]> collectEdges(Object graph, Identifier dimension) throws Exception {
        List<float[]> polylines = new ArrayList<>();
        Set<Object> nodeLocations = (Set<Object>) mGetNodes.invoke(graph);
        if (nodeLocations == null) return polylines;
        Set<Long> visited = new HashSet<>();

        for (Object loc : nodeLocations) {
            if (loc == null) continue;
            Object dimObj = fLocDimension.get(loc);
            if (!(dimObj instanceof RegistryKey<?> dim) || !dim.getValue().equals(dimension)) continue;

            Object node = mLocateNode.invoke(graph, loc);
            if (node == null) continue;
            Map<Object, Object> connections = (Map<Object, Object>) mGetConnectionsFrom.invoke(graph, node);
            if (connections == null) continue;

            Vec3i a = (Vec3i) mNodeGetLocation.invoke(node);
            for (Map.Entry<Object, Object> edgeEntry : connections.entrySet()) {
                Object other = edgeEntry.getKey();
                Object edge = edgeEntry.getValue();
                if (other == null || edge == null) continue;
                Vec3i b = (Vec3i) mNodeGetLocation.invoke(other);
                if (!visited.add(edgeKey(a, b))) continue; // each edge appears from both ends
                float[] pts = sampleEdge(graph, edge, a, b);
                if (pts != null) polylines.add(pts);
            }
        }
        return polylines;
    }

    private static float[] sampleEdge(Object graph, Object edge, Vec3i a, Vec3i b) throws Exception {
        boolean turn = (boolean) mEdgeIsTurn.invoke(edge);
        if (!turn) {
            // Track node coordinates are stored at 2x resolution.
            return new float[] {
                (float) (a.getX() / 2.0), (float) (a.getZ() / 2.0),
                (float) (b.getX() / 2.0), (float) (b.getZ() / 2.0)
            };
        }
        float[] pts = new float[(CURVE_SAMPLES + 1) * 2];
        for (int i = 0; i <= CURVE_SAMPLES; i++) {
            double t = i / (double) CURVE_SAMPLES;
            Vec3d v = (Vec3d) mEdgeGetPosition.invoke(edge, graph, t);
            pts[i * 2] = (float) v.x;
            pts[i * 2 + 1] = (float) v.z;
        }
        return pts;
    }

    /** Order-independent 64-bit key for a node pair, so each edge is drawn once. */
    private static long edgeKey(Vec3i a, Vec3i b) {
        long ha = ((long) a.getX() * 31 + a.getY()) * 31 + a.getZ();
        long hb = ((long) b.getX() * 31 + b.getY()) * 31 + b.getZ();
        long lo = Math.min(ha, hb);
        long hi = Math.max(ha, hb);
        return lo * 1000003L + hi;
    }
}
