package dev.routesim.bench;

import dev.routesim.model.Depot;
import dev.routesim.model.Location;
import dev.routesim.model.Order;
import dev.routesim.model.Problem;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Seeded random scenarios: same seed in, same scenario out. */
public final class ScenarioGenerator {
    public static final Location DEFAULT_CENTER = new Location(51.5074, -0.1278);

    private ScenarioGenerator() {}

    public static List<Order> randomOrders(long seed, int n, Location center, double radiusKm,
                                           int maxParcelSize, int firstId) {
        Random rnd = new Random(seed);
        List<Order> orders = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            double r = radiusKm * Math.sqrt(rnd.nextDouble()); // sqrt => uniform over the disc
            double theta = 2 * Math.PI * rnd.nextDouble();
            double lat = center.lat() + r * Math.cos(theta) / 111.32;
            double lng = center.lng() + r * Math.sin(theta) / (111.32 * Math.cos(Math.toRadians(center.lat())));
            int size = 1 + rnd.nextInt(maxParcelSize);
            orders.add(new Order(firstId + i, new Location(lat, lng), size));
        }
        return orders;
    }

    public static Problem generate(long seed, int n, int capacity, Location center, double radiusKm, int maxParcelSize) {
        return Problem.haversine(new Depot(center), randomOrders(seed, n, center, radiusKm, maxParcelSize, 1), capacity);
    }

    public static Problem generate(long seed, int n) {
        return generate(seed, n, 100, DEFAULT_CENTER, 15, 10);
    }
}
