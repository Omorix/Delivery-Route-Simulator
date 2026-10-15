package dev.routesim;

import dev.routesim.bench.ScenarioGenerator;
import dev.routesim.model.Location;
import dev.routesim.model.Problem;
import dev.routesim.solver.Solvers;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Compares the heuristics with the true optimum, found by exact subset DP, on instances small enough (n=10) to solve exactly. */
class OptimalityGapTest {

    /** Exact CVRP optimum: best TSP cost per subset (Held-Karp), then best partition of all orders into capacity-feasible subsets. */
    static double optimum(Problem p) {
        int n = p.size(), full = 1 << n;
        double INF = Double.MAX_VALUE / 4;
        double[][] dp = new double[full][n];
        for (double[] row : dp) Arrays.fill(row, INF);
        for (int i = 0; i < n; i++) dp[1 << i][i] = p.dist(0, i + 1);
        for (int mask = 1; mask < full; mask++)
            for (int last = 0; last < n; last++) {
                if ((mask & (1 << last)) == 0 || dp[mask][last] >= INF) continue;
                for (int next = 0; next < n; next++) {
                    if ((mask & (1 << next)) != 0) continue;
                    int nm = mask | (1 << next);
                    dp[nm][next] = Math.min(dp[nm][next], dp[mask][last] + p.dist(last + 1, next + 1));
                }
            }
        double[] tour = new double[full];
        int[] load = new int[full];
        for (int mask = 1; mask < full; mask++) {
            double best = INF;
            for (int last = 0; last < n; last++)
                if ((mask & (1 << last)) != 0) best = Math.min(best, dp[mask][last] + p.dist(last + 1, 0));
            tour[mask] = best;
            for (int i = 0; i < n; i++) if ((mask & (1 << i)) != 0) load[mask] += p.demand(i + 1);
        }
        double[] best = new double[full];
        Arrays.fill(best, INF);
        best[0] = 0;
        for (int mask = 1; mask < full; mask++) {
            int low = Integer.numberOfTrailingZeros(mask); // the route containing the lowest unserved order
            for (int sub = mask; sub > 0; sub = (sub - 1) & mask) {
                if ((sub & (1 << low)) == 0 || load[sub] > p.capacity()) continue;
                best[mask] = Math.min(best[mask], best[mask ^ sub] + tour[sub]);
            }
        }
        return best[full - 1];
    }

    @Test
    void heuristicsStayCloseToTheTrueOptimum() {
        int seeds = 30;
        double gapGreedy = 0, gapSavings = 0, gapLs = 0;
        for (long seed = 1; seed <= seeds; seed++) {
            Problem p = ScenarioGenerator.generate(seed, 10, 30, new Location(51.5074, -0.1278), 10, 10);
            double opt = optimum(p);
            double g = Solvers.byName("greedy").solve(p).totalDistance();
            double s = Solvers.byName("savings").solve(p).totalDistance();
            double l = Solvers.byName("savings+ls").solve(p).totalDistance();
            assertTrue(l >= opt - 1e-6 && s >= opt - 1e-6 && g >= opt - 1e-6, "a heuristic beat the exact optimum - bug in the DP or the validator");
            gapGreedy += 100 * (g / opt - 1) / seeds;
            gapSavings += 100 * (s / opt - 1) / seeds;
            gapLs += 100 * (l / opt - 1) / seeds;
        }
        System.out.printf("OPTIMALITY n=10, %d seeds: greedy +%.2f%%  savings +%.2f%%  savings+ls +%.2f%% above optimum%n",
                seeds, gapGreedy, gapSavings, gapLs);
        assertTrue(gapLs < gapSavings, "local search should reduce the gap");
        assertTrue(gapLs < 5.0, "savings+ls should be within 5% of optimal on tiny instances, was " + gapLs);
    }
}
