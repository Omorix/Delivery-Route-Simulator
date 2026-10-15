package dev.routesim.model;

import java.util.List;

/** Pre-computed symmetric distances. Index 0 is the depot, 1..n are orders. */
public final class DistanceMatrix {
    private final double[][] d;

    private DistanceMatrix(double[][] d) {
        this.d = d;
    }

    public static DistanceMatrix haversine(List<Location> points) {
        int n = points.size();
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++)
                d[i][j] = d[j][i] = Geo.haversineKm(points.get(i), points.get(j));
        return new DistanceMatrix(d);
    }

    /** Plain Euclidean on (lat=x, lng=y). {@code rounded} = CVRPLIB's EUC_2D convention (nearest integer). */
    public static DistanceMatrix euclidean(List<Location> points, boolean rounded) {
        int n = points.size();
        double[][] d = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = i + 1; j < n; j++) {
                double dx = points.get(i).lat() - points.get(j).lat();
                double dy = points.get(i).lng() - points.get(j).lng();
                double v = Math.sqrt(dx * dx + dy * dy);
                d[i][j] = d[j][i] = rounded ? Math.floor(v + 0.5) : v;
            }
        return new DistanceMatrix(d);
    }

    public double get(int i, int j) {
        return d[i][j];
    }

    public int size() {
        return d.length;
    }
}
