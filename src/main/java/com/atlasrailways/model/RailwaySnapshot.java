package com.atlasrailways.model;

import java.util.List;

/*
 * Immutable, zoom-independent description of every rail network in one dimension, baked in
 * world-block coordinates. Built off-render from Create's live graph (see CreateRailways) and
 * cached; the overlay re-projects it to the screen each frame.
 *
 * Because it stays in world coordinates, panning and zooming the atlas never touch Create's data
 * or reflection again; each frame just re-projects these points.
 */
public record RailwaySnapshot(List<RailLine> lines) {

    public static final RailwaySnapshot EMPTY = new RailwaySnapshot(List.of());

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    /** One connected track network = one coloured "subway line". */
    public record RailLine(int colorArgb, List<float[]> polylines, List<Station> stations) {
        /**
         * @param polylines each entry is a flat {@code [x0, z0, x1, z1, ...]} array of world-block
         *                  coordinates describing one continuous track segment (straight edges have
         *                  two points; curves are pre-sampled into several).
         */
        public RailLine {
        }
    }

    /** A Create train station, positioned at its station block. */
    public record Station(double x, double z, String name) {
    }
}
