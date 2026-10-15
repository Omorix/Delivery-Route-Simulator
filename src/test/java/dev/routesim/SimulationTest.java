package dev.routesim;

import dev.routesim.bench.ScenarioGenerator;
import dev.routesim.model.Location;
import dev.routesim.model.Order;
import dev.routesim.sim.Simulation;
import dev.routesim.solver.Solvers;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimulationTest {
    private static final Location DEPOT = ScenarioGenerator.DEFAULT_CENTER;

    private static Simulation loaded(String algo, int n) {
        Simulation sim = new Simulation(DEPOT, 100, Solvers.byName(algo));
        sim.addOrders(ScenarioGenerator.randomOrders(5, n, DEPOT, 15, 10, 1));
        return sim;
    }

    private static void runToEnd(Simulation sim) {
        for (int i = 0; i < 100_000 && !sim.isFinished(); i++) sim.advance(30);
        assertTrue(sim.isFinished(), "simulation never finished");
    }

    @Test
    void everyOrderIsDeliveredAndVansTravelThePlannedDistance() {
        Simulation sim = loaded("savings+ls", 60);
        double planned = sim.snapshot().stats().plannedDistanceKm();
        runToEnd(sim);
        Simulation.Stats s = sim.snapshot().stats();
        assertEquals(60, s.deliveredOrders());
        assertEquals(planned, s.travelledKm(), 1e-6);
        sim.snapshot().vans().forEach(v -> {
            assertEquals("AT_DEPOT", v.state());
            assertEquals(0, v.remainingStops());
        });
    }

    @Test
    void eventsRunInTimeOrderRegardlessOfStepSize() {
        Simulation a = loaded("savings", 40), b = loaded("savings", 40);
        for (int i = 0; i < 20_000; i++) a.advance(1);
        b.advance(20_000);
        assertEquals(a.snapshot().stats().deliveredOrders(), b.snapshot().stats().deliveredOrders());
        assertEquals(a.snapshot().stats().travelledKm(), b.snapshot().stats().travelledKm(), 1e-6);
        assertEquals(a.now(), b.now(), 1e-6);
    }

    @Test
    void vansActuallyMove() {
        Simulation sim = loaded("savings", 30);
        Simulation.VanView before = sim.snapshot().vans().get(0);
        sim.advance(300);
        Simulation.VanView after = sim.snapshot().vans().get(0);
        assertTrue(Math.abs(before.lat() - after.lat()) + Math.abs(before.lng() - after.lng()) > 0);
        assertTrue(sim.snapshot().stats().travelledKm() > 0);
    }

    @Test
    void liveOrderIsInsertedAndDelivered() {
        Simulation sim = loaded("savings+ls", 30);
        sim.advance(900); // 15 sim-minutes in
        assertFalse(sim.isFinished());
        int vansBefore = sim.snapshot().stats().vans();
        Location nearDepot = new Location(DEPOT.lat() + 0.01, DEPOT.lng() + 0.01);
        Order added = sim.addOrder(nearDepot, 3);

        assertEquals(31, sim.snapshot().stats().totalOrders());
        assertTrue(sim.plan().routes().stream().anyMatch(r -> r.path().contains(nearDepot)), "new order is on some route");
        runToEnd(sim);
        assertEquals(31, sim.snapshot().stats().deliveredOrders());
        assertTrue(added.id() > 30);
        // inserted into an existing route (or at worst one extra van), never lost
        assertTrue(sim.snapshot().stats().vans() <= vansBefore + 1);
        assertEquals(sim.snapshot().stats().plannedDistanceKm(), sim.snapshot().stats().travelledKm(), 1e-6);
    }

    @Test
    void liveOrderNeverOverloadsAVan() {
        Simulation sim = loaded("savings", 40);
        sim.advance(600);
        for (int i = 0; i < 15; i++) {
            sim.addOrder(new Location(DEPOT.lat() + 0.002 * i, DEPOT.lng() - 0.003 * i), 10);
            sim.advance(60);
        }
        sim.plan().routes().forEach(r -> assertTrue(r.load() <= 100, "van " + r.vanId() + " load " + r.load()));
        runToEnd(sim);
        assertEquals(55, sim.snapshot().stats().deliveredOrders());
    }

    @Test
    void orderAddedAfterEveryoneIsHomeWakesAnIdleVan() {
        Simulation sim = loaded("savings", 10);
        runToEnd(sim);
        sim.addOrder(new Location(DEPOT.lat() + 0.02, DEPOT.lng()), 5);
        assertFalse(sim.isFinished());
        runToEnd(sim);
        assertEquals(11, sim.snapshot().stats().deliveredOrders());
    }

    @Test
    void orderAddedAtTimeZeroIsReoptimisedIntoThePlan() {
        Simulation sim = loaded("savings+ls", 20);
        sim.addOrder(new Location(DEPOT.lat() + 0.05, DEPOT.lng()), 4);
        assertEquals(21, sim.plan().routes().stream().mapToInt(r -> r.path().size() - 2).sum());
    }

    @Test
    void switchingAlgorithmReplansTheSameOrders() {
        Simulation sim = loaded("greedy", 80);
        double greedy = sim.snapshot().stats().plannedDistanceKm();
        sim.setSolver(Solvers.byName("savings+ls"));
        Simulation.Stats s = sim.snapshot().stats();
        assertEquals("savings+ls", s.algorithm());
        assertEquals(80, s.totalOrders());
        assertTrue(s.plannedDistanceKm() < greedy);
        assertEquals(0, s.simTimeSeconds());
    }

    @Test
    void rejectsBadParcelSizes() {
        Simulation sim = loaded("greedy", 5);
        assertThrows(IllegalArgumentException.class, () -> sim.addOrder(DEPOT, 0));
        assertThrows(IllegalArgumentException.class, () -> sim.addOrder(DEPOT, 101));
        assertEquals(5, sim.snapshot().stats().totalOrders());
    }

    @Test
    void emptySimulationIsHarmless() {
        Simulation sim = new Simulation(DEPOT, 100, Solvers.byName("savings"));
        sim.advance(1000);
        assertFalse(sim.isFinished());
        assertEquals(0, sim.snapshot().stats().totalOrders());
        sim.addOrders(List.of(new Order(0, new Location(DEPOT.lat() + 0.01, DEPOT.lng()), 1)));
        runToEnd(sim);
    }
}
