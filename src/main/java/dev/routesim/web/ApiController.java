package dev.routesim.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** REST API. Every mutating call returns the full new state so the UI never has to guess. */
@RestController
@RequestMapping("/api")
public class ApiController {
    public record LatLng(double lat, double lng) {}

    public record OrderRequest(double lat, double lng, Integer size) {}

    public record ScenarioRequest(Long seed, Integer orders) {}

    public record SpeedRequest(double multiplier) {}

    public record AlgorithmRequest(String name) {}

    private final SimulationService service;

    public ApiController(SimulationService service) {
        this.service = service;
    }

    @GetMapping("/state")
    public SimulationService.State state() {
        return service.state();
    }

    @GetMapping("/compare")
    public SimulationService.Comparison compare() {
        return service.compare();
    }

    /** Create a scenario: a seeded random one. */
    @PostMapping("/scenario/random")
    public SimulationService.State randomScenario(@RequestBody ScenarioRequest req) {
        long seed = req.seed() == null ? System.nanoTime() : req.seed();
        int n = req.orders() == null ? 40 : req.orders();
        return service.randomScenario(seed, n);
    }

    @PostMapping("/scenario/clear")
    public SimulationService.State clear() {
        return service.clear();
    }

    @PostMapping("/scenario/depot")
    public SimulationService.State depot(@RequestBody LatLng depot) {
        return service.setDepot(depot.lat(), depot.lng());
    }

    /** Add an order. If the simulation clock is running it is inserted live into the cheapest feasible route. */
    @PostMapping("/orders")
    public SimulationService.State addOrder(@RequestBody OrderRequest req) {
        return service.addOrder(req.lat(), req.lng(), req.size() == null ? 5 : req.size());
    }

    @PostMapping("/sim/start")
    public SimulationService.State start() {
        return service.start();
    }

    @PostMapping("/sim/pause")
    public SimulationService.State pause() {
        return service.pause();
    }

    @PostMapping("/sim/reset")
    public SimulationService.State reset() {
        return service.reset();
    }

    @PostMapping("/sim/speed")
    public SimulationService.State speed(@RequestBody SpeedRequest req) {
        return service.setSpeed(req.multiplier());
    }

    /** Switch algorithm: re-plans the same orders from t=0 (greedy | savings | savings+ls). */
    @PostMapping("/algorithm")
    public SimulationService.State algorithm(@RequestBody AlgorithmRequest req) {
        return service.setAlgorithm(req.name());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", e.getMessage()));
    }
}
