package dev.routesim.bench;

import dev.routesim.model.Problem;
import dev.routesim.model.Solution;
import dev.routesim.solver.ClarkeWrightSolver;
import dev.routesim.solver.NearestNeighbourSolver;
import dev.routesim.solver.SavingsLocalSearchSolver;
import dev.routesim.solver.Solver;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Prints Markdown tables you can paste straight into the README.
 * <pre>
 *   java -cp target/classes dev.routesim.bench.BenchmarkRunner [--sizes 50,100,200] [--seeds 5] [--cvrplib benchmarks/cvrplib]
 * </pre>
 */
public final class BenchmarkRunner {
    private static final List<Solver> SOLVERS =
            List.of(new NearestNeighbourSolver(), new ClarkeWrightSolver(), new SavingsLocalSearchSolver());

    private record Run(double distance, double millis, int vans) {}

    public static void main(String[] args) throws IOException {
        int[] sizes = {50, 100, 200, 500};
        int seeds = 5;
        Path dir = Path.of("benchmarks/cvrplib");
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--sizes" -> sizes = Stream.of(args[i + 1].split(",")).mapToInt(Integer::parseInt).toArray();
                case "--seeds" -> seeds = Integer.parseInt(args[i + 1]);
                case "--cvrplib" -> dir = Path.of(args[i + 1]);
                default -> { }
            }
        }
        synthetic(sizes, seeds);
        cvrplib(dir);
    }

    private static Run time(Solver s, Problem p) {
        long t0 = System.nanoTime();
        Solution sol = s.solve(p);
        double ms = (System.nanoTime() - t0) / 1e6;
        List<String> errors = sol.validate(p);
        if (!errors.isEmpty()) throw new IllegalStateException(s.name() + " produced an invalid solution: " + errors);
        return new Run(sol.totalDistance(), ms, sol.vehiclesUsed());
    }

    private static void synthetic(int[] sizes, int seeds) {
        // warm up the JIT so the first row's runtimes are comparable
        Problem warm = ScenarioGenerator.generate(999, 100);
        for (int i = 0; i < 3; i++) SOLVERS.forEach(s -> s.solve(warm));

        System.out.println("### Seeded random scenarios (Haversine km, van capacity 100, parcels 1-10, mean of "
                + seeds + " seeds)\n");
        System.out.println("| Orders | Greedy km | Savings km | Savings+LS km | Savings vs greedy | Savings+LS vs greedy "
                + "| Greedy ms | Savings ms | Savings+LS ms | Vans (greedy / S+LS) |");
        System.out.println("|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
        for (int n : sizes) {
            double[] dist = new double[3], ms = new double[3], vans = new double[3];
            for (int seed = 1; seed <= seeds; seed++) {
                Problem p = ScenarioGenerator.generate(seed, n);
                for (int k = 0; k < SOLVERS.size(); k++) {
                    Run r = time(SOLVERS.get(k), p);
                    dist[k] += r.distance() / seeds;
                    ms[k] += r.millis() / seeds;
                    vans[k] += (double) r.vans() / seeds;
                }
            }
            System.out.println(String.format(Locale.ROOT,
                    "| %d | %.1f | %.1f | %.1f | -%.1f%% | -%.1f%% | %.1f | %.1f | %.1f | %.1f / %.1f |",
                    n, dist[0], dist[1], dist[2],
                    100 * (1 - dist[1] / dist[0]), 100 * (1 - dist[2] / dist[0]),
                    ms[0], ms[1], ms[2], vans[0], vans[2]));
        }
        System.out.println();
    }

    private static void cvrplib(Path dir) throws IOException {
        if (!Files.isDirectory(dir)) {
            System.out.println("_No CVRPLIB instances found in " + dir + " - download the Augerat A set `.vrp` files there._");
            return;
        }
        List<Path> files;
        try (Stream<Path> s = Files.list(dir)) {
            files = s.filter(f -> f.toString().endsWith(".vrp")).sorted().toList();
        }
        if (files.isEmpty()) {
            System.out.println("_No .vrp files in " + dir + " - download the Augerat A set there to get the gap table._");
            return;
        }
        Map<String, Double> best = loadBestKnown();
        System.out.println("### CVRPLIB (Augerat A set) - gap to best-known solution\n");
        System.out.println("| Instance | Best known | Greedy | gap | Savings | gap | Savings+LS | gap | Vans (S+LS) | S+LS ms |");
        System.out.println("|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|");
        List<Double> gaps = new ArrayList<>();
        for (Path f : files) {
            CvrplibParser.Instance inst = CvrplibParser.parse(f);
            Double bks = best.get(inst.name());
            Run g = time(SOLVERS.get(0), inst.problem());
            Run s = time(SOLVERS.get(1), inst.problem());
            Run l = time(SOLVERS.get(2), inst.problem());
            gaps.add(gap(l.distance(), bks));
            System.out.println(String.format(Locale.ROOT, "| %s | %s | %.0f | %s | %.0f | %s | %.0f | %s | %d | %.0f |",
                    inst.name(), bks == null ? "?" : String.format(Locale.ROOT, "%.0f", bks),
                    g.distance(), pct(g.distance(), bks), s.distance(), pct(s.distance(), bks),
                    l.distance(), pct(l.distance(), bks), l.vans(), l.millis()));
        }
        double avg = gaps.stream().filter(x -> !Double.isNaN(x)).mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
        System.out.println(String.format(Locale.ROOT, "%n**Average Savings+LS gap: %.2f%%** over %d instances%n", avg, gaps.size()));
    }

    private static double gap(double ours, Double bks) {
        return bks == null ? Double.NaN : 100 * (ours - bks) / bks;
    }

    private static String pct(double ours, Double bks) {
        return bks == null ? "?" : String.format(Locale.ROOT, "%.1f%%", gap(ours, bks));
    }

    private static Map<String, Double> loadBestKnown() throws IOException {
        Map<String, Double> m = new HashMap<>();
        try (var in = BenchmarkRunner.class.getResourceAsStream("/benchmarks/best_known.csv")) {
            if (in == null) return m;
            for (String line : new String(in.readAllBytes()).split("\\R")) {
                if (line.startsWith("#") || line.startsWith("name") || line.isBlank()) continue;
                String[] t = line.split(",");
                m.put(t[0].trim(), Double.parseDouble(t[1].trim()));
            }
        }
        return m;
    }
}
