package dev.routesim.model;

import java.util.ArrayList;
import java.util.List;

/** One van's trip: depot -> stops (node indices) -> depot. */
public record Route(Vehicle vehicle, List<Integer> stops, double distance, int load) {

    public static Route of(Problem p, Vehicle vehicle, List<Integer> stops) {
        double dist = 0;
        int load = 0;
        int prev = 0;
        for (int s : stops) {
            dist += p.dist(prev, s);
            load += p.demand(s);
            prev = s;
        }
        dist += p.dist(prev, 0);
        return new Route(vehicle, List.copyOf(stops), dist, load);
    }

    /** Full node path including the depot at both ends. */
    public List<Integer> path() {
        List<Integer> path = new ArrayList<>();
        path.add(0);
        path.addAll(stops);
        path.add(0);
        return path;
    }
}
