package dev.routesim.model;

/** A point on Earth (or, for CVRPLIB instances, an abstract x/y point stored as lat/lng). */
public record Location(double lat, double lng) {}
