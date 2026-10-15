package dev.routesim.solver;

import dev.routesim.model.Problem;
import dev.routesim.model.Solution;

import java.util.ArrayList;
import java.util.List;

/**
 * Baseline. Fill one van at a time: always drive to the nearest undelivered order that still fits;
 * when nothing fits, return to the depot and start the next van.
 */
public final class NearestNeighbourSolver implements Solver {
    @Override
    public String name() {
        return "greedy";
    }

    @Override
    public Solution solve(Problem p) {
        int n = p.size();
        boolean[] done = new boolean[n + 1];
        List<List<Integer>> routes = new ArrayList<>();
        int remaining = n;
        while (remaining > 0) {
            List<Integer> route = new ArrayList<>();
            int load = 0;
            int cur = 0;
            while (true) {
                int best = -1;
                double bestD = Double.MAX_VALUE;
                for (int j = 1; j <= n; j++) {
                    if (done[j] || load + p.demand(j) > p.capacity()) continue;
                    double d = p.dist(cur, j);
                    if (d < bestD) {
                        bestD = d;
                        best = j;
                    }
                }
                if (best < 0) break;
                done[best] = true;
                remaining--;
                load += p.demand(best);
                route.add(best);
                cur = best;
            }
            routes.add(route);
        }
        return Solution.of(p, routes);
    }
}
