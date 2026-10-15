package dev.routesim.solver;

import dev.routesim.model.Problem;
import dev.routesim.model.Solution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Clarke-Wright savings (parallel version).
 * Start with one van per order. Merging the routes of i and j saves
 * s(i,j) = d(0,i) + d(0,j) - d(i,j). Take savings largest first and merge whenever
 * i and j are the two ends of different routes and the combined load fits in a van.
 */
public final class ClarkeWrightSolver implements Solver {
    private record Saving(int i, int j, double value) {}

    @Override
    public String name() {
        return "savings";
    }

    @Override
    public Solution solve(Problem p) {
        return Solution.of(p, buildRoutes(p));
    }

    List<List<Integer>> buildRoutes(Problem p) {
        int n = p.size();
        List<Saving> savings = new ArrayList<>(n * (n - 1) / 2);
        for (int i = 1; i <= n; i++)
            for (int j = i + 1; j <= n; j++) {
                double s = p.dist(0, i) + p.dist(0, j) - p.dist(i, j);
                if (s > 1e-12) savings.add(new Saving(i, j, s));
            }
        savings.sort(Comparator.comparingDouble(Saving::value).reversed()
                .thenComparingInt(Saving::i).thenComparingInt(Saving::j));

        @SuppressWarnings("unchecked")
        List<Integer>[] routes = new List[n + 1];
        int[] load = new int[n + 1];
        int[] routeOf = new int[n + 1];
        for (int i = 1; i <= n; i++) {
            routes[i] = new ArrayList<>(List.of(i));
            load[i] = p.demand(i);
            routeOf[i] = i;
        }

        for (Saving s : savings) {
            int ri = routeOf[s.i()], rj = routeOf[s.j()];
            if (ri == rj || load[ri] + load[rj] > p.capacity()) continue;
            List<Integer> a = routes[ri], b = routes[rj];
            int i = s.i(), j = s.j();
            List<Integer> merged;
            if (last(a) == i && first(b) == j) {
                merged = concat(a, b);
            } else if (first(a) == i && last(b) == j) {
                merged = concat(b, a);
            } else if (first(a) == i && first(b) == j) {
                merged = concat(reversed(a), b);
            } else if (last(a) == i && last(b) == j) {
                merged = concat(a, reversed(b));
            } else {
                continue; // i or j is interior to its route
            }
            routes[ri] = merged;
            routes[rj] = null;
            load[ri] += load[rj];
            for (int node : merged) routeOf[node] = ri;
        }

        List<List<Integer>> result = new ArrayList<>();
        for (int r = 1; r <= n; r++) if (routes[r] != null) result.add(routes[r]);
        return result;
    }

    private static int first(List<Integer> l) { return l.get(0); }
    private static int last(List<Integer> l) { return l.get(l.size() - 1); }

    private static List<Integer> reversed(List<Integer> l) {
        List<Integer> r = new ArrayList<>(l);
        Collections.reverse(r);
        return r;
    }

    private static List<Integer> concat(List<Integer> a, List<Integer> b) {
        List<Integer> r = new ArrayList<>(a.size() + b.size());
        r.addAll(a);
        r.addAll(b);
        return r;
    }
}
