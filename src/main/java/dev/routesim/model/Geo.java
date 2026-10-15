package dev.routesim.model;

/** Geographic helpers. */
public final class Geo {
    public static final double EARTH_RADIUS_KM = 6371.0088;

    private Geo() {}

    /** Great-circle ("straight line over the globe") distance in km. */
    public static double haversineKm(Location a, Location b) {
        double dLat = Math.toRadians(b.lat() - a.lat());
        double dLng = Math.toRadians(b.lng() - a.lng());
        double h = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(Math.toRadians(a.lat())) * Math.cos(Math.toRadians(b.lat()))
                * Math.pow(Math.sin(dLng / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }
}
