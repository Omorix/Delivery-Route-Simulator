# CVRPLIB instances

Drop the Augerat "A" set `.vrp` files (e.g. `A-n32-k5.vrp`) in this folder, then run:

    mvn -q package -DskipTests
    java -cp target/classes dev.routesim.bench.BenchmarkRunner --sizes 50,100

The runner prints a second table with the gap to the best-known solution for every instance it finds.
Best-known values live in `src/main/resources/benchmarks/best_known.csv` - check them against CVRPLIB before you publish a number.

Notes on comparing fairly:
- CVRPLIB uses `EUC_2D`: distances are rounded to the nearest integer. The parser does the same, so costs are directly comparable.
- Our solvers do not cap the number of vans. If the "Vans" column is larger than the `k` in the instance name, say so in the README.
