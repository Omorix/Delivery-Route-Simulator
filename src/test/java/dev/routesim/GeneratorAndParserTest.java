package dev.routesim;

import dev.routesim.bench.CvrplibParser;
import dev.routesim.bench.ScenarioGenerator;
import dev.routesim.model.Problem;
import dev.routesim.model.Solution;
import dev.routesim.solver.Solvers;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorAndParserTest {

    @Test
    void sameSeedGivesSameScenario() {
        assertEquals(ScenarioGenerator.generate(42, 50).orders(), ScenarioGenerator.generate(42, 50).orders());
        assertNotEquals(ScenarioGenerator.generate(42, 50).orders(), ScenarioGenerator.generate(43, 50).orders());
    }

    private static final String TINY = """
            NAME : T-n5-k2
            COMMENT : (tiny test instance)
            TYPE : CVRP
            DIMENSION : 5
            EDGE_WEIGHT_TYPE : EUC_2D
            CAPACITY : 10
            NODE_COORD_SECTION
             1 0 0
             2 0 10
             3 0 20
             4 10 0
             5 20 0
            DEMAND_SECTION
            1 0
            2 5
            3 5
            4 5
            5 5
            DEPOT_SECTION
             1
             -1
            EOF
            """;

    @Test
    void parsesCvrplibFormat() {
        CvrplibParser.Instance inst = CvrplibParser.parse(TINY);
        assertEquals("T-n5-k2", inst.name());
        Problem p = inst.problem();
        assertEquals(4, p.size());
        assertEquals(10, p.capacity());
        assertEquals(10, p.dist(0, 1), 1e-9);
        assertEquals(14, p.dist(1, 3), 1e-9); // (0,10)->(10,0): sqrt(200)=14.14 -> rounded to 14, as EUC_2D specifies
    }

    @Test
    void solvesTinyInstanceOptimally() {
        // two arms of the "L": 0-10-20 up and 0-10-20 right. Optimal = 2 vans x 40 = 80.
        Problem p = CvrplibParser.parse(TINY).problem();
        for (String algo : new String[]{"savings", "savings+ls"}) {
            Solution s = Solvers.byName(algo).solve(p);
            assertEquals(80, s.totalDistance(), 1e-9, algo);
            assertEquals(2, s.vehiclesUsed());
        }
    }

    @Test
    void rejectsUnsupportedWeightType() {
        assertThrows(IllegalArgumentException.class,
                () -> CvrplibParser.parse(TINY.replace("EUC_2D", "GEO")));
    }
}
