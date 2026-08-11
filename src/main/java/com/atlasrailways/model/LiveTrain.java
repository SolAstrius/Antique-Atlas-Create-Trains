package com.atlasrailways.model;

/*
 * A single train's live position, read fresh from Create every frame (not cached, since trains
 * move). Coordinates are world-block; heading is radians in the X/Z plane (0 = +X), pointing the
 * way the train is travelling. speedBps is blocks per second (already sign-corrected to magnitude).
 */
public record LiveTrain(double x, double z, double heading, String name, double speedBps, int colorArgb) {
}
