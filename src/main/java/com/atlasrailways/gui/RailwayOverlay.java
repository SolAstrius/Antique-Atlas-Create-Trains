package com.atlasrailways.gui;

import com.atlasrailways.create.CreateRailways;
import com.atlasrailways.create.RailwayCache;
import com.atlasrailways.model.LiveTrain;
import com.atlasrailways.model.RailwaySnapshot;
import com.atlasrailways.net.LiveTrainStore;
import folk.sisby.antique_atlas.gui.AtlasOverlay;
import folk.sisby.antique_atlas.gui.AtlasRenderer;
import folk.sisby.antique_atlas.gui.AtlasScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

import java.util.List;

/*
 * Paints Create's rail networks onto the Antique Atlas book: each network as a coloured line
 * following the real track geometry, with a roundel marker at every station. Hovering a station
 * shows its name in a tooltip, like the vanilla structure markers do. The cached world-space
 * RailwaySnapshot is re-projected each frame, so panning and zooming need no extra work (the atlas
 * already scissors drawing to the map area).
 */
public class RailwayOverlay implements AtlasOverlay {

    private static final int PARCHMENT = 0xFFF4E7C6; // station roundel centre
    private static final int INK = 0xFF241A10;       // roundel outline
    private static final int TIP_BG = 0xF01A120A;
    private static final int TIP_BORDER = 0xFF6B4A2A;
    private static final int TIP_TEXT = 0xFFF2E4C0;

    private static final int DISC_SEGMENTS = 16;
    private static final float CULL_MARGIN = 24.0f;

    // Layered z, so outlines sit under fills and everything sits above the tiles.
    private static final float Z_LINE = 0.02f;
    private static final float Z_OUTLINE = 0.05f;
    private static final float Z_RING = 0.06f;
    private static final float Z_CENTRE = 0.07f;
    private static final float Z_TRAIN_OUT = 0.09f;
    private static final float Z_TRAIN = 0.10f;

    /** Width of an arrowhead corner from the heading, in radians (~137deg). */
    private static final double ARROW_SPREAD = 2.4;

    /** Whether the rail overlay (lines + station markers) is shown. Toggled by the side-tab button. */
    public static boolean visible = true;
    /** Whether live train markers are shown. Toggled by its own side-tab button. */
    public static boolean trainsVisible = true;

    @Override
    public void onScreenInit(AtlasScreen screen) {
        RailwayToggleButton button = new RailwayToggleButton();
        screen.addChild(button);
        screen.offsetSideButton(button);
        button.setTitle(titleFor(visible));
        // Momentary, like the built-in "reset scale" tab: never stays selected, so it keeps the
        // hover slide-out animation instead of sitting permanently extended.
        button.addListener(b -> {
            visible = !visible;
            button.setTitle(titleFor(visible));
            button.setSelected(false);
        });

        // A second, separate tab for the live train markers.
        TrainToggleButton trainButton = new TrainToggleButton();
        screen.addChild(trainButton);
        screen.offsetSideButton(trainButton);
        trainButton.setTitle(trainTitleFor(trainsVisible));
        trainButton.addListener(b -> {
            trainsVisible = !trainsVisible;
            trainButton.setTitle(trainTitleFor(trainsVisible));
            trainButton.setSelected(false);
        });
    }

    private static Text titleFor(boolean shown) {
        return Text.literal(shown ? "Hide railways" : "Show railways");
    }

    private static Text trainTitleFor(boolean shown) {
        return Text.literal(shown ? "Hide live trains" : "Show live trains");
    }

    @Override
    public void onScreenRender(AtlasScreenRenderContext context) {
        if (!visible && !trainsVisible) return;

        AtlasScreen screen = context.screen();
        Identifier dim = screen.dim().getValue();

        DrawContext dc = context.context();
        MatrixStack matrices = dc.getMatrices();
        Matrix4f m = matrices.peek().getPositionMatrix();

        double ppb = screen.getPixelsPerBlock();
        float lineWidth = clampf((float) (ppb * 2.5) + 1.0f, 1.5f, 6.0f);
        // Station markers stay a readable size at any zoom, like map markers do.
        double radius = clampf((float) (ppb * 1.2) + 3.5f, 4.0f, 7.5f);
        double outlineRadius = radius + 1.4;
        double centreRadius = radius * 0.42;

        int bookX = screen.bookX();
        int bookY = screen.bookY();

        double minX = AtlasRenderer.MAP_BORDER_WIDTH - CULL_MARGIN;
        double minY = AtlasRenderer.MAP_BORDER_HEIGHT - CULL_MARGIN;
        double maxX = AtlasRenderer.MAP_BORDER_WIDTH + screen.mapWidth() + CULL_MARGIN;
        double maxY = AtlasRenderer.MAP_BORDER_HEIGHT + screen.mapHeight() + CULL_MARGIN;

        // ---- Rails + stations (own toggle) --------------------------------
        RailwaySnapshot.Station hoveredStation = null;
        if (visible) {
            RailwaySnapshot snapshot = RailwayCache.getInstance().get(dim);
            if (!snapshot.isEmpty()) {
                // 1) Rail lines: one batched QUADS draw for every segment of every network.
                BufferBuilder lines = OverlayDraw.beginQuads();
                for (RailwaySnapshot.RailLine line : snapshot.lines()) {
                    int color = line.colorArgb();
                    for (float[] poly : line.polylines()) {
                        double prevX = 0, prevY = 0;
                        boolean hasPrev = false;
                        for (int i = 0; i + 1 < poly.length; i += 2) {
                            double x = screen.worldXToScreenX(poly[i]) - bookX;
                            double y = screen.worldZToScreenY(poly[i + 1]) - bookY;
                            if (hasPrev && segmentVisible(prevX, prevY, x, y, minX, minY, maxX, maxY)) {
                                OverlayDraw.segment(lines, m, prevX, prevY, x, y, lineWidth, color, Z_LINE);
                                OverlayDraw.joint(lines, m, x, y, lineWidth, color, Z_LINE);
                            }
                            prevX = x;
                            prevY = y;
                            hasPrev = true;
                        }
                    }
                }
                OverlayDraw.flush(lines);

                // 2) Station roundels: ink outline, then coloured ring, then parchment centre.
                BufferBuilder outline = OverlayDraw.beginTriangles();
                forEachVisibleStation(screen, snapshot, bookX, bookY, minX, minY, maxX, maxY,
                    (st, x, y, color) -> OverlayDraw.disc(outline, m, x, y, outlineRadius, INK, DISC_SEGMENTS, Z_OUTLINE));
                OverlayDraw.flush(outline);

                BufferBuilder ring = OverlayDraw.beginTriangles();
                forEachVisibleStation(screen, snapshot, bookX, bookY, minX, minY, maxX, maxY,
                    (st, x, y, color) -> OverlayDraw.disc(ring, m, x, y, radius, color, DISC_SEGMENTS, Z_RING));
                OverlayDraw.flush(ring);

                BufferBuilder centre = OverlayDraw.beginTriangles();
                forEachVisibleStation(screen, snapshot, bookX, bookY, minX, minY, maxX, maxY,
                    (st, x, y, color) -> OverlayDraw.disc(centre, m, x, y, centreRadius, PARCHMENT, DISC_SEGMENTS, Z_CENTRE));
                OverlayDraw.flush(centre);

                hoveredStation = pickStation(screen, snapshot, context.mouseX(), context.mouseY(), radius);
            }
        }

        // ---- Live trains (own toggle) -------------------------------------
        LiveTrain hoveredTrain = null;
        double trainSize = clampf((float) (ppb * 1.4) + 4.0f, 4.5f, 9.0f);
        if (trainsVisible) {
            // Prefer the server's global stream (all trains, anywhere); otherwise fall back to
            // reading nearby trains locally (client-only, limited to loaded trains).
            List<LiveTrain> trains;
            if (LiveTrainStore.hasRecentData()) {
                trains = LiveTrainStore.get(dim);
            } else if (CreateRailways.trainsAvailable()) {
                trains = CreateRailways.readTrains(dim);
            } else {
                trains = List.of();
            }
            if (!trains.isEmpty()) {
                // Arrowhead pointing along travel direction: ink outline, then coloured fill.
                BufferBuilder tOut = OverlayDraw.beginTriangles();
                for (LiveTrain t : trains) {
                    double x = screen.worldXToScreenX(t.x()) - bookX;
                    double y = screen.worldZToScreenY(t.z()) - bookY;
                    if (x < minX || x > maxX || y < minY || y > maxY) continue;
                    arrow(tOut, m, x, y, t.heading(), trainSize + 1.5, INK, Z_TRAIN_OUT);
                }
                OverlayDraw.flush(tOut);

                BufferBuilder tFill = OverlayDraw.beginTriangles();
                for (LiveTrain t : trains) {
                    double x = screen.worldXToScreenX(t.x()) - bookX;
                    double y = screen.worldZToScreenY(t.z()) - bookY;
                    if (x < minX || x > maxX || y < minY || y > maxY) continue;
                    arrow(tFill, m, x, y, t.heading(), trainSize, t.colorArgb(), Z_TRAIN);
                }
                OverlayDraw.flush(tFill);

                hoveredTrain = pickTrain(screen, trains, context.mouseX(), context.mouseY(), trainSize);
            }
        }

        // ---- Hover tooltip (train takes priority when both overlap) -------
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        if (hoveredTrain != null) {
            double x = screen.worldXToScreenX(hoveredTrain.x()) - bookX;
            double y = screen.worldZToScreenY(hoveredTrain.z()) - bookY;
            String label = (hoveredTrain.name().isEmpty() ? "Train" : hoveredTrain.name())
                + "  -  " + String.format("%.1f blocks/s", hoveredTrain.speedBps());
            drawTooltip(dc, tr, label, x, y, trainSize, minX, minY, maxX, maxY);
        } else if (hoveredStation != null) {
            double x = screen.worldXToScreenX(hoveredStation.x()) - bookX;
            double y = screen.worldZToScreenY(hoveredStation.z()) - bookY;
            String name = hoveredStation.name().isEmpty() ? "Station" : hoveredStation.name();
            drawTooltip(dc, tr, name, x, y, radius, minX, minY, maxX, maxY);
        }
    }

    /** Draws an arrowhead centred at (cx, cy) pointing along {@code heading} (radians). */
    private static void arrow(BufferBuilder bb, Matrix4f m, double cx, double cy, double heading,
                              double size, int argb, float z) {
        double tipX = cx + Math.cos(heading) * size;
        double tipY = cy + Math.sin(heading) * size;
        double lX = cx + Math.cos(heading + ARROW_SPREAD) * size;
        double lY = cy + Math.sin(heading + ARROW_SPREAD) * size;
        double rX = cx + Math.cos(heading - ARROW_SPREAD) * size;
        double rY = cy + Math.sin(heading - ARROW_SPREAD) * size;
        OverlayDraw.triangle(bb, m, tipX, tipY, lX, lY, rX, rY, argb, z);
    }

    private LiveTrain pickTrain(AtlasScreen screen, List<LiveTrain> trains, Integer mouseX, Integer mouseY, double size) {
        if (mouseX == null || mouseY == null) return null;
        double hit = size + 3;
        double best = hit * hit;
        LiveTrain bestTrain = null;
        for (LiveTrain t : trains) {
            double dx = screen.worldXToScreenX(t.x()) - mouseX;
            double dy = screen.worldZToScreenY(t.z()) - mouseY;
            double d2 = dx * dx + dy * dy;
            if (d2 < best) {
                best = d2;
                bestTrain = t;
            }
        }
        return bestTrain;
    }

    /** A vanilla-marker-style tooltip: dark box, sepia border, parchment text, anchored to the station. */
    private void drawTooltip(DrawContext dc, TextRenderer tr, String name,
                             double stationX, double stationY, double radius,
                             double minX, double minY, double maxX, double maxY) {
        int w = tr.getWidth(name);
        int boxW = w + 8;
        int boxH = tr.fontHeight + 6;
        int x = (int) Math.round(stationX + radius + 4);
        int y = (int) Math.round(stationY - boxH / 2.0);
        // Keep the whole box inside the visible map so the scissor never clips it.
        if (x + boxW > maxX) x = (int) Math.round(stationX - radius - 4 - boxW);
        x = (int) clamp(x, minX + CULL_MARGIN, maxX - CULL_MARGIN - boxW);
        y = (int) clamp(y, minY + CULL_MARGIN, maxY - CULL_MARGIN - boxH);

        dc.fill(x, y, x + boxW, y + boxH, TIP_BG);
        dc.fill(x, y, x + boxW, y + 1, TIP_BORDER);
        dc.fill(x, y + boxH - 1, x + boxW, y + boxH, TIP_BORDER);
        dc.fill(x, y, x + 1, y + boxH, TIP_BORDER);
        dc.fill(x + boxW - 1, y, x + boxW, y + boxH, TIP_BORDER);
        dc.drawText(tr, name, x + 4, y + 4, TIP_TEXT, false);
    }

    private interface StationVisitor {
        void visit(RailwaySnapshot.Station st, double screenX, double screenY, int color);
    }

    private void forEachVisibleStation(AtlasScreen screen, RailwaySnapshot snapshot, int bookX, int bookY,
                                       double minX, double minY, double maxX, double maxY, StationVisitor v) {
        for (RailwaySnapshot.RailLine line : snapshot.lines()) {
            for (RailwaySnapshot.Station st : line.stations()) {
                double x = screen.worldXToScreenX(st.x()) - bookX;
                double y = screen.worldZToScreenY(st.z()) - bookY;
                if (x < minX || x > maxX || y < minY || y > maxY) continue;
                v.visit(st, x, y, line.colorArgb());
            }
        }
    }

    private RailwaySnapshot.Station pickStation(AtlasScreen screen, RailwaySnapshot snapshot,
                                                Integer mouseX, Integer mouseY, double radius) {
        if (mouseX == null || mouseY == null) return null;
        double hit = radius + 3;
        double best = hit * hit;
        RailwaySnapshot.Station bestStation = null;
        for (RailwaySnapshot.RailLine line : snapshot.lines()) {
            for (RailwaySnapshot.Station st : line.stations()) {
                double x = screen.worldXToScreenX(st.x()); // absolute screen space, matches mouse
                double y = screen.worldZToScreenY(st.z());
                double dx = x - mouseX;
                double dy = y - mouseY;
                double d2 = dx * dx + dy * dy;
                if (d2 < best) {
                    best = d2;
                    bestStation = st;
                }
            }
        }
        return bestStation;
    }

    private static boolean segmentVisible(double x1, double y1, double x2, double y2,
                                          double minX, double minY, double maxX, double maxY) {
        return Math.max(x1, x2) >= minX && Math.min(x1, x2) <= maxX
            && Math.max(y1, y2) >= minY && Math.min(y1, y2) <= maxY;
    }

    private static float clampf(float v, float lo, float hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }
}
