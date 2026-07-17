package resiliencylens;

import java.util.*;

/**
 * Unit-style validation tests to verify the correctness of the custom Tarjan's Bridge-Finding algorithm.
 */
public class TarjanTest {

    public static void main(String[] args) {
        System.out.println("=================================================");
        System.out.println("Running Tarjan's Bridge-Finding Algorithm Tests");
        System.out.println("=================================================");

        testCycleWithBridge();
        testTreeStructure();
        testCycleOnly();
        testDisconnectedComponents();

        System.out.println("\nAll tests executed successfully!");
    }

    private static void assertTest(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError("Test FAILED: " + message);
        }
        System.out.println("[PASS] " + message);
    }

    private static boolean hasBridge(List<NetworkGraph.Bridge> bridges, String u, String v) {
        for (NetworkGraph.Bridge b : bridges) {
            if ((b.getU().equals(u) && b.getV().equals(v)) || (b.getU().equals(v) && b.getV().equals(u))) {
                return true;
            }
        }
        return false;
    }

    private static NetworkGraph.Bridge getBridge(List<NetworkGraph.Bridge> bridges, String u, String v) {
        for (NetworkGraph.Bridge b : bridges) {
            if ((b.getU().equals(u) && b.getV().equals(v)) || (b.getU().equals(v) && b.getV().equals(u))) {
                return b;
            }
        }
        return null;
    }

    private static void testCycleWithBridge() {
        // Topology:
        // A - B    D - E
        //  \ /      \ /
        //   C ------ D
        // Here, C - D is the bridge connecting two cliques/cycles.
        NetworkGraph graph = new NetworkGraph();
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "A");
        graph.addEdge("C", "D");
        graph.addEdge("D", "E");
        graph.addEdge("E", "F");
        graph.addEdge("F", "D");

        List<NetworkGraph.Bridge> bridges = graph.findBridges();
        
        assertTest(bridges.size() == 1, "CycleWithBridge: Should find exactly 1 bridge");
        assertTest(hasBridge(bridges, "C", "D"), "CycleWithBridge: The bridge should be C <-> D");

        NetworkGraph.Bridge bridge = getBridge(bridges, "C", "D");
        assertTest(bridge != null, "Bridge C-D exists");
        
        // Let's check blast radius
        // Starting at D and forbidding C: D, E, F are isolated.
        // Starting at C and forbidding D: A, B, C are isolated.
        // In our algorithm, if DFS visited C first and D as descendant, the bridge will be u=C, v=D,
        // and blast radius is computed for D, which should contain [D, E, F].
        Set<String> blast = bridge.getBlastRadius();
        assertTest(blast.contains("D") && blast.contains("E") && blast.contains("F"), 
            "CycleWithBridge: Blast radius for C <-> D contains D, E, F");
        assertTest(blast.size() == 3, "CycleWithBridge: Blast radius size is 3");
    }

    private static void testTreeStructure() {
        // Topology: A - B - C - D
        // Every edge is a bridge!
        NetworkGraph graph = new NetworkGraph();
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "D");

        List<NetworkGraph.Bridge> bridges = graph.findBridges();

        assertTest(bridges.size() == 3, "TreeStructure: Should find exactly 3 bridges");
        assertTest(hasBridge(bridges, "A", "B"), "TreeStructure: Contains A-B");
        assertTest(hasBridge(bridges, "B", "C"), "TreeStructure: Contains B-C");
        assertTest(hasBridge(bridges, "C", "D"), "TreeStructure: Contains C-D");
    }

    private static void testCycleOnly() {
        // Topology: A - B - C - A
        // A single cycle has no bridges.
        NetworkGraph graph = new NetworkGraph();
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "A");

        List<NetworkGraph.Bridge> bridges = graph.findBridges();

        assertTest(bridges.isEmpty(), "CycleOnly: Should find 0 bridges");
    }

    private static void testDisconnectedComponents() {
        // Component 1: A - B - C - A (No bridges)
        // Component 2: D - E (Bridge D - E)
        NetworkGraph graph = new NetworkGraph();
        graph.addEdge("A", "B");
        graph.addEdge("B", "C");
        graph.addEdge("C", "A");

        graph.addEdge("D", "E");

        List<NetworkGraph.Bridge> bridges = graph.findBridges();

        assertTest(bridges.size() == 1, "DisconnectedComponents: Should find exactly 1 bridge");
        assertTest(hasBridge(bridges, "D", "E"), "DisconnectedComponents: Bridge is D <-> E");
    }
}
