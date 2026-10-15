package dev.routesim;

import dev.routesim.bench.ScenarioGenerator;
import dev.routesim.model.Depot;
import dev.routesim.model.Geo;
import dev.routesim.model.Location;
import dev.routesim.model.Order;
import dev.routesim.model.Problem;
import dev.routesim.model.Route;
import dev.routesim.model.Solution;
import dev.routesim.solver.Solver;
import dev.routesim.solver.Solvers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SolverInvariantsTest {

    @Test
    void haversineMatchesKnownDistance() {
        double london_paris = Geo.haversineKm(new Location(51.5074, -0.1278), new Location(48.8566, 2.3522));
        assertEquals(343.5, london_paris, 2.0);
        assertEquals(0, Geo.haversineKm(new Location(10, 10), new Location(10, 10)), 1e-12);
    }

    /** The three rules from the brief: every order exactly once, no van over capacity, every route depot-to-depot. */
    @ParameterizedTest(name = "{0}, n={1}, seed={2}")
    @CsvSource({
            "greedy,1,1", "greedy,25,1", "greedy,80,2", "greedy,200,3",
            "savings,1,1", "savings,25,1", "savings,80,2", "savings,200,3",
            "savings+ls,1,1", "savings+ls,25,1", "savings+ls,80,2", "savings+ls,200,3"})
    void everySolverProducesAValidSolution(String algorithm, int n, long seed) {
        Problem p = ScenarioGenerator.generate(seed, n);
        Solution s = Solvers.byName(algorithm).solve(p);

        assertEquals(List.of(), s.validate(p));
        // spell the three rules out independently of Solution.validate
        int[] times = new int[n + 1];
        for (Route r : s.routes()) {
            assertEquals(0, r.path().get(0), "route must start at the depot");
            assertEquals(0, r.path().get(r.path().size() - 1), "route must end at the depot");
            assertTrue(r.load() <= p.capacity(), "van over capacity");
            r.stops().forEach(stop -> times[stop]++);
        }
        for (int i = 1; i <= n; i++) assertEquals(1, times[i], "order " + i + " must be delivered exactly once");
    }

    @Test
    void validatorCatchesBrokenSolutions() {
        Problem p = ScenarioGenerator.generate(1, 10);
        Solution missing = Solution.of(p, List.of(List.of(1, 2, 3)));
        assertFalse(missing.validate(p).isEmpty());
        Solution dup = Solution.of(p, List.of(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10), List.of(1)));
        assertTrue(dup.validate(p).stream().anyMatch(e -> e.contains("2 times")));
    }

    @Test
    void capacityIsRespectedWhenParcelsAreHuge() {
        // every parcel fills 60% of a van -> each order needs its own van
        List<Order> orders = List.of(
                new Order(1, new Location(51.51, -0.12), 60),
                new Order(2, new Location(51.52, -0.13), 60),
                new Order(3, new Location(51.50, -0.10), 60));
        Problem p = Problem.haversine(new Depot(new Location(51.5, -0.12)), orders, 100);
        for (String name : Solvers.names()) {
            Solution s = Solvers.byName(name).solve(p);
            assertEquals(3, s.vehiclesUsed(), name);
            assertEquals(List.of(), s.validate(p), name);
        }
    }

    @Test
    void oversizedParcelIsRejectedUpFront() {
        List<Order> orders = List.of(new Order(1, new Location(51.51, -0.12), 101));
        assertThrows(IllegalArgumentException.class,
                () -> Problem.haversine(new Depot(new Location(51.5, -0.12)), orders, 100));
    }

    @Test
    void emptyProblemGivesEmptySolution() {
        Problem p = Problem.haversine(new Depot(new Location(51.5, -0.12)), List.of(), 100);
        for (String name : Solvers.names()) assertEquals(0, Solvers.byName(name).solve(p).vehiclesUsed());
    }

    @Test
    void optimisationNeverLosesToItsStartingPoint() {
        for (long seed = 1; seed <= 10; seed++) {
            Problem p = ScenarioGenerator.generate(seed, 80);
            double savings = Solvers.byName("savings").solve(p).totalDistance();
            double withLs = Solvers.byName("savings+ls").solve(p).totalDistance();
            assertTrue(withLs <= savings + 1e-9, "local search made seed " + seed + " worse");
        }
    }

    @Test
    void savingsBeatsGreedyOnAverage() {
        double greedy = 0, savings = 0, ls = 0;
        for (long seed = 1; seed <= 10; seed++) {
            Problem p = ScenarioGenerator.generate(seed, 100);
            greedy += Solvers.byName("greedy").solve(p).totalDistance();
            savings += Solvers.byName("savings").solve(p).totalDistance();
            ls += Solvers.byName("savings+ls").solve(p).totalDistance();
        }
        assertTrue(savings < greedy, "savings " + savings + " vs greedy " + greedy);
        assertTrue(ls < savings, "savings+ls " + ls + " vs savings " + savings);
    }

    @ParameterizedTest
    @CsvSource({"greedy", "savings", "savings+ls"})
    void solversAreDeterministic(String algorithm) {
        Solver s = Solvers.byName(algorithm);
        Problem p = ScenarioGenerator.generate(7, 60);
        assertEquals(s.solve(p).routes().stream().map(Route::stops).toList(),
                s.solve(p).routes().stream().map(Route::stops).toList());
    }
}
