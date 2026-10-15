package dev.routesim.solver;

import java.util.List;

public final class Solvers {
    private Solvers() {}

    public static List<String> names() {
        return List.of("greedy", "savings", "savings+ls");
    }

    public static Solver byName(String name) {
        return switch (name) {
            case "greedy" -> new NearestNeighbourSolver();
            case "savings" -> new ClarkeWrightSolver();
            case "savings+ls" -> new SavingsLocalSearchSolver();
            default -> throw new IllegalArgumentException("unknown algorithm '" + name + "', expected one of " + names());
        };
    }
}
