package dev.routesim.model;

import java.util.ArrayList;
import java.util.List;

public record Solution(List<Route> routes) {

    public static Solution of(Problem p, List<? extends List<Integer>> stopLists) {
        List<Route> routes = new ArrayList<>();
        int id = 1;
        for (List<Integer> stops : stopLists) {
            if (stops.isEmpty()) continue;
            routes.add(Route.of(p, new Vehicle(id++, p.capacity()), stops));
        }
        return new Solution(List.copyOf(routes));
    }

    public double totalDistance() {
        return routes.stream().mapToDouble(Route::distance).sum();
    }

    public int vehiclesUsed() {
        return routes.size();
    }

    /** Hard-constraint check. Returns human-readable violations (empty = valid). */
    public List<String> validate(Problem p) {
        List<String> errors = new ArrayList<>();
        int[] seen = new int[p.size() + 1];
        for (Route r : routes) {
            List<Integer> path = r.path();
            if (path.get(0) != 0 || path.get(path.size() - 1) != 0)
                errors.add("vehicle " + r.vehicle().id() + " does not start and end at the depot");
            if (r.stops().isEmpty()) errors.add("vehicle " + r.vehicle().id() + " has an empty route");
            int load = 0;
            for (int s : r.stops()) {
                if (s < 1 || s > p.size()) {
                    errors.add("unknown stop " + s);
                    continue;
                }
                seen[s]++;
                load += p.demand(s);
            }
            if (load > p.capacity())
                errors.add("vehicle " + r.vehicle().id() + " carries " + load + " > capacity " + p.capacity());
        }
        for (int i = 1; i <= p.size(); i++) {
            if (seen[i] == 0) errors.add("order " + p.order(i).id() + " is never delivered");
            if (seen[i] > 1) errors.add("order " + p.order(i).id() + " is delivered " + seen[i] + " times");
        }
        return errors;
    }
}
