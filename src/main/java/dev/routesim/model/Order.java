package dev.routesim.model;

/** A delivery: where it goes and how much van capacity the parcel takes. */
public record Order(int id, Location location, int size) {}
