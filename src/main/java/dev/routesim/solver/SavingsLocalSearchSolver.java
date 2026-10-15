package dev.routesim.solver;

import dev.routesim.model.Problem;
import dev.routesim.model.Solution;

/** Clarke-Wright construction followed by local search. */
public final class SavingsLocalSearchSolver implements Solver {
    private final ClarkeWrightSolver construction = new ClarkeWrightSolver();
    private final LocalSearch search;

    public SavingsLocalSearchSolver() {
        this(5_000);
    }

    public SavingsLocalSearchSolver(long timeLimitMillis) {
        this.search = new LocalSearch(timeLimitMillis);
    }

    @Override
    public String name() {
        return "savings+ls";
    }

    @Override
    public Solution solve(Problem p) {
        return Solution.of(p, search.improve(p, construction.buildRoutes(p)));
    }
}
