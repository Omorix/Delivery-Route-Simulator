# 2-minute walkthrough (interview script)

**0:00 - What and why (15 s)**
"It's a delivery dispatch simulator. Given a depot, a set of orders with parcel sizes and vans with a capacity limit, it plans routes, then replays them live on a map - and you can add orders while the vans are moving. The part I care about is that the routing algorithms are my own, and I measured them."

**0:15 - The result (15 s)**
"Against a greedy nearest-neighbour baseline, my optimiser cuts total distance by about 15 to 22 percent on seeded scenarios from 50 to 500 orders. On ten-order instances I can compute the true optimum exactly, and I'm about 2 percent above it." (Add the CVRPLIB gap here once you've run it.)

**0:30 - How the optimiser works (35 s)**
"Step one is Clarke-Wright savings. Start with one van per order, and merge two routes whenever joining them saves the most distance - d(depot,i) plus d(depot,j) minus d(i,j) - as long as the van still has capacity. Step two is local search on top: 2-opt to remove crossings inside a route, relocate and swap to move stops between vans, and 2-opt-star to swap route tails. Every move is capacity-checked and only applied if it shortens the total, so it can't make things worse or break a rule."

**1:05 - The simulation (30 s)**
"It's discrete-event: a priority queue of timed events - van departs, van arrives. The clock jumps from event to event instead of ticking, so it's exact and independent of step size - I test that. A scheduled executor just calls advance with the elapsed time times a speed multiplier. When a new order arrives mid-run I try every legal insertion point in every van, pick the cheapest, and if every van is full I dispatch a new one."

**1:35 - Testing and shipping (15 s)**
"Tests check the hard rules - every order delivered exactly once, no van over capacity, every route depot to depot - on all three solvers, plus determinism, the simulation, and an exact-optimum comparison. It's Dockerised and CI runs the tests on every push."

**1:50 - Honest limits (10 s)**
"Distances are straight-line, not roads; next step would be OSRM and time windows."

---

## Questions you should expect

- **Why Clarke-Wright and not just local search from greedy?** Better starting point; savings is much closer to optimal, so local search converges faster to a better result.
- **Is your local search optimal?** No: it stops at a local optimum. That's the 2% gap. Next step would be simulated annealing or iterated local search.
- **What's the complexity?** Savings is O(n^2 log n) from sorting the savings list. Each local-search scan is O(n^2) per neighbourhood.
- **How do you know the solutions are valid?** `Solution.validate` plus property-style tests over many seeds and sizes.
- **Why is the simulation not tied to wall-clock time?** Determinism and testability; the real-time runner is a thin layer on top.
- **What breaks if two people use it at once?** It's one shared simulation by design; `Simulation` methods are synchronized. A real system would key sessions.
- **What's the weakest part?** Be ready to say: live insertion assumes parcels can be handed to a moving van; road distances are straight-line; no time windows.
