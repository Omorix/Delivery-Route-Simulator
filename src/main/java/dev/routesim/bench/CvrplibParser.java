package dev.routesim.bench;

import dev.routesim.model.DistanceMatrix;
import dev.routesim.model.Depot;
import dev.routesim.model.Location;
import dev.routesim.model.Order;
import dev.routesim.model.Problem;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Reads CVRPLIB / TSPLIB-style .vrp files (EUC_2D only, depot = node 1), e.g. the Augerat "A" set. */
public final class CvrplibParser {
    public record Instance(String name, Problem problem) {}

    private CvrplibParser() {}

    public static Instance parse(Path file) throws IOException {
        return parse(Files.readString(file));
    }

    public static Instance parse(String text) {
        String name = "unnamed";
        int capacity = -1;
        String weightType = "EUC_2D";
        List<double[]> coords = new ArrayList<>();
        List<Integer> demands = new ArrayList<>();
        String section = "";
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.equals("EOF")) continue;
            if (line.contains(":")) {
                String key = line.substring(0, line.indexOf(':')).trim();
                String val = line.substring(line.indexOf(':') + 1).trim();
                switch (key) {
                    case "NAME" -> name = val;
                    case "CAPACITY" -> capacity = Integer.parseInt(val);
                    case "EDGE_WEIGHT_TYPE" -> weightType = val;
                    default -> { /* COMMENT, TYPE, DIMENSION ... not needed */ }
                }
                section = "";
                continue;
            }
            if (line.endsWith("_SECTION")) {
                section = line;
                continue;
            }
            String[] t = line.split("\\s+");
            switch (section) {
                case "NODE_COORD_SECTION" ->
                        coords.add(new double[]{Double.parseDouble(t[1]), Double.parseDouble(t[2])});
                case "DEMAND_SECTION" -> demands.add((int) Double.parseDouble(t[1]));
                default -> { /* DEPOT_SECTION: depot is assumed to be node 1 */ }
            }
        }
        if (!weightType.equals("EUC_2D")) throw new IllegalArgumentException("unsupported EDGE_WEIGHT_TYPE " + weightType);
        if (capacity <= 0 || coords.isEmpty() || coords.size() != demands.size())
            throw new IllegalArgumentException("malformed instance " + name);

        List<Location> pts = new ArrayList<>();
        for (double[] c : coords) pts.add(new Location(c[0], c[1]));
        List<Order> orders = new ArrayList<>();
        for (int i = 1; i < pts.size(); i++) orders.add(new Order(i, pts.get(i), demands.get(i)));
        Problem p = new Problem(new Depot(pts.get(0)), orders, capacity, DistanceMatrix.euclidean(pts, true));
        return new Instance(name, p);
    }
}
