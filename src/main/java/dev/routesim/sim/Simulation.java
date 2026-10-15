package dev.routesim.sim;

import dev.routesim.model.Depot;
import dev.routesim.model.Geo;
import dev.routesim.model.Location;
import dev.routesim.model.Order;
import dev.routesim.model.Problem;
import dev.routesim.model.Route;
import dev.routesim.model.Solution;
import dev.routesim.solver.Solver;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Discrete-event dispatch simulation.
 * <p>
 * Time only moves by {@link #advance(double)}: the clock jumps from event to event (van departs / van arrives)
 * held in a {@link PriorityQueue} ordered by time. The class is deliberately single-threaded in spirit and has no
 * wall-clock dependency, so it is fully deterministic and unit-testable; {@link SimulationRunner} drives it in real time.
 * <p>
 * Orders added once the clock has started are inserted live into the cheapest feasible route.
 * The capacity rule is the same hard rule the solvers use (everything assigned to a van, delivered or not, must fit).
 * Simplification (documented in the README): the new parcel is assumed to be handed to the van on the road.
 */
public final class Simulation {
    public static final double DEFAULT_SPEED_KMH = 40;
    public static final double DEFAULT_SERVICE_SECONDS = 120;

    // ---- public view types -------------------------------------------------------------------------------
    public record VanView(int id, double lat, double lng, String state, int remainingStops, int delivered,
                          double distanceKm, int load) {}

    public record Stats(String algorithm, double simTimeSeconds, int totalOrders, int deliveredOrders,
                        double plannedDistanceKm, double travelledKm, double utilisation, double lastSolveMillis,
                        int vans, boolean finished) {}

    public record Snapshot(Stats stats, List<VanView> vans, int planVersion) {}

    /** orderIds are in visiting order; path = depot, one point per order, depot. */
    public record RouteView(int vanId, List<Integer> orderIds, List<Location> path, double distanceKm, int load) {}

    public record PlanView(Location depot, int capacity, List<Order> orders, List<RouteView> routes, int planVersion) {}

    // ---- internals ---------------------------------------------------------------------------------------
    private enum VanState { AT_DEPOT, READY, TO_STOP, SERVICING, RETURNING }

    private enum EventType { DEPART, ARRIVE }

    private record Event(double time, long seq, EventType type, Van van, int epoch) implements Comparable<Event> {
        @Override
        public int compareTo(Event o) {
            int c = Double.compare(time, o.time);
            return c != 0 ? c : Long.compare(seq, o.seq);
        }
    }

    private final class Van {
        final int id;
        VanState state = VanState.READY;
        final Deque<Order> stops = new ArrayDeque<>(); // first element = current target while TO_STOP
        final List<Order> done = new ArrayList<>();
        Location anchor = depot;                         // where the van last stood still
        Location legFrom, legTo;
        double legStart, legEnd;
        double travelled;                                // completed legs only
        int epoch;                                       // bumped to invalidate stale ARRIVE events

        Van(int id) { this.id = id; }

        Location positionAt(double t) {
            if (state != VanState.TO_STOP && state != VanState.RETURNING) return anchor;
            double span = legEnd - legStart;
            double f = span <= 0 ? 1 : Math.max(0, Math.min(1, (t - legStart) / span));
            return new Location(legFrom.lat() + (legTo.lat() - legFrom.lat()) * f,
                    legFrom.lng() + (legTo.lng() - legFrom.lng()) * f);
        }

        double travelledAt(double t) {
            boolean moving = state == VanState.TO_STOP || state == VanState.RETURNING;
            return travelled + (moving ? Geo.haversineKm(legFrom, positionAt(t)) : 0);
        }

        int remainingLoad() { return stops.stream().mapToInt(Order::size).sum(); }

        int totalLoad() { return remainingLoad() + done.stream().mapToInt(Order::size).sum(); }
    }

    private final int capacity;
    private final double speedKmh;
    private final double serviceSeconds;
    private Location depot;
    private Solver solver;
    private final List<Order> orders = new ArrayList<>();
    private int nextOrderId = 1;

    private final PriorityQueue<Event> events = new PriorityQueue<>();
    private final List<Van> vans = new ArrayList<>();
    private double now;
    private long seq;
    private int planVersion;
    private double lastSolveMillis;

    public Simulation(Location depot, int capacity, Solver solver) {
        this(depot, capacity, solver, DEFAULT_SPEED_KMH, DEFAULT_SERVICE_SECONDS);
    }

    public Simulation(Location depot, int capacity, Solver solver, double speedKmh, double serviceSeconds) {
        this.depot = depot;
        this.capacity = capacity;
        this.solver = solver;
        this.speedKmh = speedKmh;
        this.serviceSeconds = serviceSeconds;
    }

    // ---- scenario control --------------------------------------------------------------------------------

    public synchronized void setDepot(Location newDepot) {
        this.depot = newDepot;
        reset();
    }

    public synchronized void setSolver(Solver newSolver) {
        this.solver = newSolver;
        reset();
    }

    public synchronized String algorithm() { return solver.name(); }

    public synchronized void clearOrders() {
        orders.clear();
        reset();
    }

    /** Add several orders at once (before the clock starts, this plans once rather than once per order). */
    public synchronized void addOrders(List<Order> newOrders) {
        for (Order o : newOrders) {
            checkSize(o.size());
            orders.add(new Order(nextOrderId++, o.location(), o.size()));
        }
        reset();
    }

    /**
     * Add one order. At t=0 the whole plan is simply re-optimised; once vans are moving the order is inserted
     * into the cheapest feasible route (or a fresh van is dispatched if nothing can take it).
     */
    public synchronized Order addOrder(Location where, int size) {
        checkSize(size);
        Order o = new Order(nextOrderId++, where, size);
        orders.add(o);
        if (now == 0 || vans.isEmpty()) reset();
        else {
            insertLive(o);
            planVersion++;
        }
        return o;
    }

    private void checkSize(int size) {
        if (size < 1 || size > capacity)
            throw new IllegalArgumentException("parcel size must be between 1 and the van capacity (" + capacity + ")");
    }

    /** Back to t=0: re-solve with the current algorithm and park all vans at the depot, ready to leave. */
    public synchronized void reset() {
        now = 0;
        seq = 0;
        events.clear();
        vans.clear();
        planVersion++;
        if (orders.isEmpty()) {
            lastSolveMillis = 0;
            return;
        }
        Problem p = Problem.haversine(new Depot(depot), orders, capacity);
        long t0 = System.nanoTime();
        Solution s = solver.solve(p);
        lastSolveMillis = (System.nanoTime() - t0) / 1e6;
        int id = 1;
        for (Route r : s.routes()) {
            Van v = new Van(id++);
            for (int node : r.stops()) v.stops.add(p.order(node));
            vans.add(v);
            schedule(0, EventType.DEPART, v);
        }
    }

    /** Solve the current orders with any algorithm without touching the running simulation (for comparisons). */
    public synchronized Solution solveWith(Solver other) {
        return other.solve(Problem.haversine(new Depot(depot), orders, capacity));
    }

    public synchronized Problem currentProblem() {
        return Problem.haversine(new Depot(depot), orders, capacity);
    }

    // ---- time --------------------------------------------------------------------------------------------

    /** Advance the simulation clock by {@code dtSeconds} of simulated time, processing every due event in order. */
    public synchronized void advance(double dtSeconds) {
        double target = now + dtSeconds;
        while (!events.isEmpty() && events.peek().time() <= target) {
            Event e = events.poll();
            now = e.time();
            handle(e);
        }
        if (!isFinished()) now = target; // once everything is delivered the clock stops at the last event
    }

    public synchronized boolean isFinished() {
        return !vans.isEmpty() && events.isEmpty() && vans.stream().allMatch(v -> v.state == VanState.AT_DEPOT);
    }

    public synchronized double now() { return now; }

    private void schedule(double time, EventType type, Van v) {
        events.add(new Event(time, seq++, type, v, v.epoch));
    }

    private void handle(Event e) {
        Van v = e.van();
        if (e.epoch() != v.epoch) return; // superseded (van was redirected)
        switch (e.type()) {
            case DEPART -> depart(v);
            case ARRIVE -> arrive(v);
        }
    }

    private void depart(Van v) {
        if (!v.stops.isEmpty()) startLeg(v, v.stops.peekFirst().location(), VanState.TO_STOP);
        else if (!v.anchor.equals(depot)) startLeg(v, depot, VanState.RETURNING);
        else v.state = VanState.AT_DEPOT;
    }

    private void startLeg(Van v, Location to, VanState moving) {
        v.legFrom = v.anchor;
        v.legTo = to;
        v.legStart = now;
        v.legEnd = now + Geo.haversineKm(v.legFrom, to) / speedKmh * 3600.0;
        v.state = moving;
        schedule(v.legEnd, EventType.ARRIVE, v);
    }

    private void arrive(Van v) {
        v.travelled += Geo.haversineKm(v.legFrom, v.legTo);
        v.anchor = v.legTo;
        if (v.state == VanState.TO_STOP) {
            v.done.add(v.stops.pollFirst());
            v.state = VanState.SERVICING;
            schedule(now + serviceSeconds, EventType.DEPART, v);
        } else {
            v.state = VanState.AT_DEPOT;
        }
    }

    // ---- dynamic orders ----------------------------------------------------------------------------------

    private void insertLive(Order o) {
        Van best = null;
        int bestK = -1;
        double bestCost = Double.MAX_VALUE;
        for (Van v : vans) {
            if (v.totalLoad() + o.size() > capacity) continue; // same hard rule as the solvers: a van never carries more than its capacity
            List<Order> list = new ArrayList<>(v.stops);
            // A van already driving to stop #0 can't change its mind; it can take the order after that stop.
            int minK = v.state == VanState.TO_STOP ? 1 : 0;
            Location refStart = v.state == VanState.RETURNING ? v.positionAt(now) : v.anchor;
            for (int k = minK; k <= list.size(); k++) {
                Location a = k == 0 ? refStart : list.get(k - 1).location();
                Location b = k == list.size() ? depot : list.get(k).location();
                double cost = Geo.haversineKm(a, o.location()) + Geo.haversineKm(o.location(), b) - Geo.haversineKm(a, b);
                if (cost < bestCost - 1e-9) {
                    bestCost = cost;
                    best = v;
                    bestK = k;
                }
            }
        }
        if (best == null) { // every van is full: dispatch a new one
            Van v = new Van(vans.size() + 1);
            v.stops.add(o);
            vans.add(v);
            schedule(now, EventType.DEPART, v);
            return;
        }
        boolean wake = best.state == VanState.AT_DEPOT;
        if (best.state == VanState.RETURNING) { // turn the van around where it is
            Location pos = best.positionAt(now);
            best.travelled += Geo.haversineKm(best.legFrom, pos);
            best.anchor = pos;
            best.epoch++;
            wake = true;
        }
        List<Order> list = new ArrayList<>(best.stops);
        list.add(bestK, o);
        best.stops.clear();
        best.stops.addAll(list);
        if (wake) {
            best.state = VanState.READY;
            schedule(now, EventType.DEPART, best);
        }
    }

    // ---- views -------------------------------------------------------------------------------------------

    public synchronized Snapshot snapshot() {
        List<VanView> views = new ArrayList<>();
        double travelled = 0;
        int delivered = 0;
        for (Van v : vans) {
            Location pos = v.positionAt(now);
            double t = v.travelledAt(now);
            travelled += t;
            int d = v.done.stream().mapToInt(x -> 1).sum();
            delivered += d;
            views.add(new VanView(v.id, pos.lat(), pos.lng(), v.state.name(), v.stops.size(), d, t, v.totalLoad()));
        }
        double util = vans.isEmpty() ? 0
                : vans.stream().mapToDouble(v -> (double) v.totalLoad() / capacity).average().orElse(0);
        Stats stats = new Stats(solver.name(), now, orders.size(), delivered, plannedDistance(), travelled, util,
                lastSolveMillis, vans.size(), isFinished());
        return new Snapshot(stats, views, planVersion);
    }

    public synchronized PlanView plan() {
        List<RouteView> routes = new ArrayList<>();
        for (Van v : vans) {
            List<Location> path = pathOf(v);
            List<Integer> ids = new ArrayList<>();
            v.done.forEach(o -> ids.add(o.id()));
            v.stops.forEach(o -> ids.add(o.id()));
            routes.add(new RouteView(v.id, ids, path, length(path), v.totalLoad()));
        }
        return new PlanView(depot, capacity, List.copyOf(orders), routes, planVersion);
    }

    private double plannedDistance() {
        return vans.stream().mapToDouble(v -> length(pathOf(v))).sum();
    }

    private List<Location> pathOf(Van v) {
        List<Location> path = new ArrayList<>();
        path.add(depot);
        for (Order o : v.done) path.add(o.location());
        for (Order o : v.stops) path.add(o.location());
        path.add(depot);
        return path;
    }

    private static double length(List<Location> path) {
        double sum = 0;
        for (int i = 1; i < path.size(); i++) sum += Geo.haversineKm(path.get(i - 1), path.get(i));
        return sum;
    }
}
