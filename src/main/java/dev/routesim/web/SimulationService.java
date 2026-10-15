package dev.routesim.web;

import dev.routesim.bench.ScenarioGenerator;
import dev.routesim.model.Depot;
import dev.routesim.model.Location;
import dev.routesim.model.Order;
import dev.routesim.model.Problem;
import dev.routesim.model.Route;
import dev.routesim.model.Solution;
import dev.routesim.sim.Simulation;
import dev.routesim.sim.SimulationRunner;
import dev.routesim.solver.NearestNeighbourSolver;
import dev.routesim.solver.SavingsLocalSearchSolver;
import dev.routesim.solver.Solver;
import dev.routesim.solver.Solvers;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** Owns the one shared simulation (a single dispatch "room" - everyone who opens the page watches the same fleet). */
@Service
public class SimulationService {
    public static final int CAPACITY = 100;
    private static final int MAX_ORDERS = 2_000;

    public record State(Simulation.Snapshot snapshot, Simulation.PlanView plan, boolean running, double speed,
                        List<String> algorithms) {}

    public record Tick(Simulation.Snapshot snapshot, boolean running, double speed) {}

    public record Side(String algorithm, double distanceKm, double solveMillis, int vans,
                       List<Simulation.RouteView> routes) {}

    public record Comparison(Location depot, List<Order> orders, Side greedy, Side optimised, double savingsPercent) {}

    private final Simulation sim;
    private final SimulationRunner runner;
    private final SimWebSocketHandler sockets;

    public SimulationService(SimWebSocketHandler sockets) {
        this.sockets = sockets;
        Location center = ScenarioGenerator.DEFAULT_CENTER;
        this.sim = new Simulation(center, CAPACITY, Solvers.byName("savings+ls"));
        this.sim.addOrders(ScenarioGenerator.randomOrders(42, 40, center, 12, 10, 1));
        this.runner = new SimulationRunner(sim, this::broadcast);
    }

    private void broadcast(Simulation.Snapshot snapshot) {
        sockets.broadcast(new Tick(snapshot, runner.isRunning(), runner.speedMultiplier()));
    }

    @PreDestroy
    void shutdown() {
        runner.close();
    }

    public State state() {
        return new State(sim.snapshot(), sim.plan(), runner.isRunning(), runner.speedMultiplier(), Solvers.names());
    }

    /** Pushes the new state to every open page and returns it to the caller. */
    private State changed() {
        State s = state();
        sockets.broadcast(new Tick(s.snapshot(), s.running(), s.speed()));
        return s;
    }

    public State start() {
        runner.start();
        return changed();
    }

    public State pause() {
        runner.pause();
        return changed();
    }

    public State reset() {
        runner.pause();
        sim.reset();
        return changed();
    }

    public State setSpeed(double multiplier) {
        runner.setSpeedMultiplier(multiplier);
        return changed();
    }

    public State setAlgorithm(String name) {
        Solver solver = Solvers.byName(name); // throws IllegalArgumentException (-> HTTP 400) for unknown names
        runner.pause();
        sim.setSolver(solver);
        return changed();
    }

    public State setDepot(double lat, double lng) {
        runner.pause();
        sim.setDepot(new Location(lat, lng));
        return changed();
    }

    public State addOrder(double lat, double lng, int size) {
        if (sim.snapshot().stats().totalOrders() >= MAX_ORDERS)
            throw new IllegalArgumentException("order limit reached (" + MAX_ORDERS + ")");
        sim.addOrder(new Location(lat, lng), size); // live insertion if the clock is running
        return changed();
    }

    public State randomScenario(long seed, int n) {
        if (n < 1 || n > MAX_ORDERS) throw new IllegalArgumentException("orders must be between 1 and " + MAX_ORDERS);
        runner.pause();
        Location depot = sim.plan().depot();
        sim.clearOrders();
        sim.addOrders(ScenarioGenerator.randomOrders(seed, n, depot, 12, 10, 1));
        return changed();
    }

    public State clear() {
        runner.pause();
        sim.clearOrders();
        return changed();
    }

    /** Same orders, solved by the greedy baseline and by the full optimiser, side by side. */
    public Comparison compare() {
        Problem p = sim.currentProblem();
        Side greedy = side(new NearestNeighbourSolver(), p);
        Side optimised = side(new SavingsLocalSearchSolver(), p);
        double saving = greedy.distanceKm() == 0 ? 0 : 100 * (1 - optimised.distanceKm() / greedy.distanceKm());
        return new Comparison(p.depot().location(), p.orders(), greedy, optimised, saving);
    }

    private Side side(Solver solver, Problem p) {
        long t0 = System.nanoTime();
        Solution s = solver.solve(p);
        double ms = (System.nanoTime() - t0) / 1e6;
        List<Simulation.RouteView> routes = new ArrayList<>();
        for (Route r : s.routes()) {
            List<Integer> ids = new ArrayList<>();
            List<Location> path = new ArrayList<>();
            path.add(p.depot().location());
            for (int node : r.stops()) {
                ids.add(p.order(node).id());
                path.add(p.order(node).location());
            }
            path.add(p.depot().location());
            routes.add(new Simulation.RouteView(r.vehicle().id(), ids, path, r.distance(), r.load()));
        }
        return new Side(solver.name(), s.totalDistance(), ms, s.vehiclesUsed(), routes);
    }
}
