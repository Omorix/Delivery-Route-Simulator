package dev.routesim.sim;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Drives a {@link Simulation} in real time: every tick the sim clock advances by
 * (tick length x speed multiplier) and a snapshot is pushed to the listener (the WebSocket broadcaster).
 */
public final class SimulationRunner implements AutoCloseable {
    public static final long TICK_MILLIS = 200;

    private final Simulation sim;
    private final Consumer<Simulation.Snapshot> listener;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "sim-clock");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> task;
    private volatile double speedMultiplier = 60; // 1 real second = 1 simulated minute

    public SimulationRunner(Simulation sim, Consumer<Simulation.Snapshot> listener) {
        this.sim = sim;
        this.listener = listener;
    }

    public synchronized void start() {
        if (task != null) return;
        task = executor.scheduleAtFixedRate(this::tick, 0, TICK_MILLIS, TimeUnit.MILLISECONDS);
    }

    public synchronized void pause() {
        if (task == null) return;
        task.cancel(false);
        task = null;
        listener.accept(sim.snapshot());
    }

    public synchronized boolean isRunning() { return task != null; }

    public void setSpeedMultiplier(double m) {
        if (m <= 0 || m > 10_000) throw new IllegalArgumentException("speed multiplier must be in (0, 10000]");
        this.speedMultiplier = m;
    }

    public double speedMultiplier() { return speedMultiplier; }

    private void tick() {
        try {
            sim.advance(TICK_MILLIS / 1000.0 * speedMultiplier);
            listener.accept(sim.snapshot());
        } catch (RuntimeException e) { // never let an exception cancel the periodic task
            e.printStackTrace();
        }
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
