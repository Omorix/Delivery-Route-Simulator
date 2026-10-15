package dev.routesim.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A capacitated vehicle routing problem: one depot, n orders, identical vans.
 * Nodes are numbered 0 (depot) and 1..n (orders.get(node - 1)).
 */
public final class Problem {
    private final Depot depot;
    private final List<Order> orders;
    private final int capacity;
    private final DistanceMatrix matrix;

    public Problem(Depot depot, List<Order> orders, int capacity, DistanceMatrix matrix) {
        if (capacity <= 0) throw new IllegalArgumentException("capacity must be positive");
        if (matrix.size() != orders.size() + 1)
            throw new IllegalArgumentException("distance matrix must cover depot + all orders");
        for (Order o : orders) {
            if (o.size() <= 0) throw new IllegalArgumentException("order " + o.id() + " has non-positive size");
            if (o.size() > capacity)
                throw new IllegalArgumentException("order " + o.id() + " (size " + o.size()
                        + ") does not fit in a van of capacity " + capacity);
        }
        this.depot = depot;
        this.orders = List.copyOf(new ArrayList<>(orders));
        this.capacity = capacity;
        this.matrix = matrix;
    }

    /** Real-world scenario: straight-line (Haversine) distances in km. */
    public static Problem haversine(Depot depot, List<Order> orders, int capacity) {
        List<Location> pts = new ArrayList<>();
        pts.add(depot.location());
        for (Order o : orders) pts.add(o.location());
        return new Problem(depot, orders, capacity, DistanceMatrix.haversine(pts));
    }

    public Depot depot() { return depot; }
    public List<Order> orders() { return orders; }
    public int capacity() { return capacity; }
    public int size() { return orders.size(); }
    public Order order(int node) { return orders.get(node - 1); }
    public int demand(int node) { return orders.get(node - 1).size(); }
    public double dist(int a, int b) { return matrix.get(a, b); }
}
