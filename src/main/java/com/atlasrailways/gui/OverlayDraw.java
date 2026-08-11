package com.atlasrailways.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;

/**
 * Low-level, batched drawing primitives for the rail overlay, using the immediate-mode
 * tessellator so an arbitrary number of segments and station roundels collapse into a handful of
 * draw calls. Geometry is expressed in the (already translated) screen space of the atlas book.
 * Each primitive takes an explicit {@code z} so passes can be layered cleanly.
 */
public final class OverlayDraw {
    private OverlayDraw() {
    }

    // --- lifecycle ------------------------------------------------------

    public static BufferBuilder beginQuads() {
        return begin(VertexFormat.DrawMode.QUADS);
    }

    public static BufferBuilder beginTriangles() {
        return begin(VertexFormat.DrawMode.TRIANGLES);
    }

    private static BufferBuilder begin(VertexFormat.DrawMode mode) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // 2D geometry has no meaningful facing; don't let back-face culling drop it.
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        return Tessellator.getInstance().begin(mode, VertexFormats.POSITION_COLOR);
    }

    public static void flush(BufferBuilder builder) {
        var built = builder.endNullable();
        if (built != null) {
            BufferRenderer.drawWithGlobalProgram(built);
        }
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
    }

    // --- geometry -------------------------------------------------------

    /** Appends a thick line segment (a rectangle) between two points into a QUADS buffer. */
    public static void segment(BufferBuilder bb, Matrix4f m, double x1, double y1, double x2, double y2,
                               float width, int argb, float z) {
        double dx = x2 - x1;
        double dy = y2 - y1;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1.0e-4) return;
        double hw = width / 2.0;
        double nx = -dy / len * hw;
        double ny = dx / len * hw;
        quad(bb, m,
            (float) (x1 + nx), (float) (y1 + ny),
            (float) (x2 + nx), (float) (y2 + ny),
            (float) (x2 - nx), (float) (y2 - ny),
            (float) (x1 - nx), (float) (y1 - ny), argb, z);
    }

    /** A small axis-aligned square centred on a point, used to fill the gap at a polyline joint. */
    public static void joint(BufferBuilder bb, Matrix4f m, double cx, double cy, float size, int argb, float z) {
        float h = size / 2.0f;
        quad(bb, m,
            (float) (cx - h), (float) (cy - h),
            (float) (cx + h), (float) (cy - h),
            (float) (cx + h), (float) (cy + h),
            (float) (cx - h), (float) (cy + h), argb, z);
    }

    private static void quad(BufferBuilder bb, Matrix4f m,
                             float ax, float ay, float bx, float by,
                             float cx, float cy, float dx, float dy, int argb, float z) {
        bb.vertex(m, ax, ay, z).color(argb);
        bb.vertex(m, bx, by, z).color(argb);
        bb.vertex(m, cx, cy, z).color(argb);
        bb.vertex(m, dx, dy, z).color(argb);
    }

    /** Appends a filled triangle into a TRIANGLES buffer (culling is off, so winding is irrelevant). */
    public static void triangle(BufferBuilder bb, Matrix4f m,
                                double x1, double y1, double x2, double y2, double x3, double y3,
                                int argb, float z) {
        bb.vertex(m, (float) x1, (float) y1, z).color(argb);
        bb.vertex(m, (float) x2, (float) y2, z).color(argb);
        bb.vertex(m, (float) x3, (float) y3, z).color(argb);
    }

    /**
     * Appends a filled disc (fan of triangles) into a TRIANGLES buffer. Vertices are wound
     * counter-clockwise in screen space (Y-down) to match the front-face the GUI expects, so the
     * disc isn't back-face culled.
     */
    public static void disc(BufferBuilder bb, Matrix4f m, double cx, double cy, double radius,
                            int argb, int segments, float z) {
        double prevX = cx + radius;
        double prevY = cy;
        for (int i = 1; i <= segments; i++) {
            double a = (i / (double) segments) * Math.PI * 2.0;
            double px = cx + Math.cos(a) * radius;
            double py = cy + Math.sin(a) * radius;
            bb.vertex(m, (float) cx, (float) cy, z).color(argb);
            bb.vertex(m, (float) px, (float) py, z).color(argb);
            bb.vertex(m, (float) prevX, (float) prevY, z).color(argb);
            prevX = px;
            prevY = py;
        }
    }
}
