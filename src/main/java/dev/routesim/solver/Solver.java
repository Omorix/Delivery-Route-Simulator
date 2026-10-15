package dev.routesim.solver;

import dev.routesim.model.Problem;
import dev.routesim.model.Solution;

public interface Solver {
    String name();

    Solution solve(Problem problem);
}
