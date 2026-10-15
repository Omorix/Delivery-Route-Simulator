package dev.routesim.solver;

import dev.routesim.model.Problem;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * First-improvement local search over four neighbourhoods:
 * <ul>
 *   <li><b>2-opt</b> - reverse a segment inside one route (removes crossings)</li>
 *   <li><b>relocate</b> - move one order to another position / another route</li>
 *   <li><b>swap</b> - exchange two orders between two routes</li>
 *   <li><b>2-opt*</b> - exchange the tails of two routes</li>
 * </ul>
 * Every move is checked against van capacity and only applied if it shortens total distance,
 * so the result is never worse than the input and always feasible.
 */
public final class LocalSearch {
    private static final double EPS = 1e-9;
    private final long timeLimitNanos;

    public LocalSearch(long timeLimitMillis) {
        this.timeLimitNanos = timeLimitMillis * 1_000_000L;
    }

    public List<List<Integer>> improve(Problem p, List<? extends List<Integer>> initial) {
        List<List<Integer>> routes = new ArrayList<>();
        for (List<Integer> r : initial) routes.add(new ArrayList<>(r));
        State s = new State(p, routes, System.nanoTime() + timeLimitNanos);
        boolean improved = true;
        while (improved && !s.timeUp()) {
            improved = s.twoOpt() || s.relocate() || s.swap() || s.twoOptStar();
        }
        return routes;
    }

    private static final class State {
        final Problem p;
        final List<List<Integer>> routes;
        final long deadline;
        int[] load;

        State(Problem p, List<List<Integer>> routes, long deadline) {
            this.p = p;
            this.routes = routes;
            this.deadline = deadline;
            refresh();
        }

        boolean timeUp() { return System.nanoTime() > deadline; }

        double d(int a, int b) { return p.dist(a, b); }

        /** Drop empty routes and recompute loads. Called after every applied move. */
        void refresh() {
            routes.removeIf(List::isEmpty);
            load = new int[routes.size()];
            for (int r = 0; r < routes.size(); r++)
                for (int node : routes.get(r)) load[r] += p.demand(node);
        }

        boolean twoOpt() {
            for (List<Integer> r : routes) {
                if (timeUp()) return false;
                int m = r.size();
                for (int i = 0; i < m - 1; i++) {
                    for (int j = i + 1; j < m; j++) {
                        int before = i == 0 ? 0 : r.get(i - 1);
                        int after = j == m - 1 ? 0 : r.get(j + 1);
                        int a = r.get(i), b = r.get(j);
                        double delta = d(before, b) + d(a, after) - d(before, a) - d(b, after);
                        if (delta < -EPS) {
                            Collections.reverse(r.subList(i, j + 1));
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        boolean relocate() {
            for (int a = 0; a < routes.size(); a++) {
                if (timeUp()) return false;
                List<Integer> A = routes.get(a);
                for (int i = 0; i < A.size(); i++) {
                    int u = A.get(i);
                    int pa = i == 0 ? 0 : A.get(i - 1);
                    int na = i == A.size() - 1 ? 0 : A.get(i + 1);
                    double removeGain = d(pa, u) + d(u, na) - d(pa, na);
                    for (int b = 0; b < routes.size(); b++) {
                        List<Integer> B = routes.get(b);
                        if (a == b) {
                            int redSize = A.size() - 1; // route without u
                            for (int k = 0; k <= redSize; k++) {
                                if (k == i) continue; // same place
                                int x = k == 0 ? 0 : red(A, i, k - 1);
                                int y = k == redSize ? 0 : red(A, i, k);
                                double delta = d(x, u) + d(u, y) - d(x, y) - removeGain;
                                if (delta < -EPS) {
                                    A.remove(i);
                                    A.add(k, u);
                                    return true;
                                }
                            }
                        } else {
                            if (load[b] + p.demand(u) > p.capacity()) continue;
                            for (int k = 0; k <= B.size(); k++) {
                                int x = k == 0 ? 0 : B.get(k - 1);
                                int y = k == B.size() ? 0 : B.get(k);
                                double delta = d(x, u) + d(u, y) - d(x, y) - removeGain;
                                if (delta < -EPS) {
                                    A.remove(i);
                                    B.add(k, u);
                                    refresh();
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
            return false;
        }

        /** Element idx of route r as if element 'skip' had been removed. */
        private static int red(List<Integer> r, int skip, int idx) {
            return idx < skip ? r.get(idx) : r.get(idx + 1);
        }

        boolean swap() {
            for (int a = 0; a < routes.size(); a++) {
                if (timeUp()) return false;
                for (int b = a + 1; b < routes.size(); b++) {
                    List<Integer> A = routes.get(a), B = routes.get(b);
                    for (int i = 0; i < A.size(); i++) {
                        int u = A.get(i);
                        int pa = i == 0 ? 0 : A.get(i - 1);
                        int na = i == A.size() - 1 ? 0 : A.get(i + 1);
                        for (int j = 0; j < B.size(); j++) {
                            int v = B.get(j);
                            if (load[a] - p.demand(u) + p.demand(v) > p.capacity()) continue;
                            if (load[b] - p.demand(v) + p.demand(u) > p.capacity()) continue;
                            int pb = j == 0 ? 0 : B.get(j - 1);
                            int nb = j == B.size() - 1 ? 0 : B.get(j + 1);
                            double delta = d(pa, v) + d(v, na) + d(pb, u) + d(u, nb)
                                    - d(pa, u) - d(u, na) - d(pb, v) - d(v, nb);
                            if (delta < -EPS) {
                                A.set(i, v);
                                B.set(j, u);
                                refresh();
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }

        boolean twoOptStar() {
            int R = routes.size();
            int[][] pref = new int[R][];
            for (int r = 0; r < R; r++) {
                List<Integer> route = routes.get(r);
                pref[r] = new int[route.size() + 1];
                for (int i = 0; i < route.size(); i++) pref[r][i + 1] = pref[r][i] + p.demand(route.get(i));
            }
            for (int a = 0; a < R; a++) {
                if (timeUp()) return false;
                for (int b = a + 1; b < R; b++) {
                    List<Integer> A = routes.get(a), B = routes.get(b);
                    for (int i = 0; i <= A.size(); i++) {
                        int lastA = i == 0 ? 0 : A.get(i - 1);
                        int firstTailA = i == A.size() ? 0 : A.get(i);
                        for (int j = 0; j <= B.size(); j++) {
                            int lastB = j == 0 ? 0 : B.get(j - 1);
                            int firstTailB = j == B.size() ? 0 : B.get(j);
                            int newA = pref[a][i] + (load[b] - pref[b][j]);
                            int newB = pref[b][j] + (load[a] - pref[a][i]);
                            if (newA > p.capacity() || newB > p.capacity()) continue;
                            double delta = d(lastA, firstTailB) + d(lastB, firstTailA)
                                    - d(lastA, firstTailA) - d(lastB, firstTailB);
                            if (delta < -EPS) {
                                List<Integer> tailA = new ArrayList<>(A.subList(i, A.size()));
                                List<Integer> tailB = new ArrayList<>(B.subList(j, B.size()));
                                A.subList(i, A.size()).clear();
                                B.subList(j, B.size()).clear();
                                A.addAll(tailB);
                                B.addAll(tailA);
                                refresh();
                                return true;
                            }
                        }
                    }
                }
            }
            return false;
        }
    }
}
