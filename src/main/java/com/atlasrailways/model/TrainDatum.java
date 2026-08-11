package com.atlasrailways.model;

import net.minecraft.util.Identifier;

/*
 * One train's state as sent over the network from server to client. Positions are world-block;
 * heading is radians (0 = +X) in travel direction; speed is blocks/second; dimension lets the
 * client draw it only on the matching map.
 */
public record TrainDatum(Identifier dimension, double x, double z, float heading, float speed,
                         int color, String name) {
}
