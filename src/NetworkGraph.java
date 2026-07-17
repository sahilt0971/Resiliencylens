package resiliencylens;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core Graph Engine representing the microservice network topology.
 * Uses an Adjacency List with thread-safe collections to support high-throughput,
 * real-time edge additions and removals.
 */
public class NetworkGraph {

    // Adjacency list: maps service node names to their set of neighbors.
    // ConcurrentHashMap allows thread-safe operations.
    // The inner Sets are created using ConcurrentHashMap.newKeySet() to allow thread-safe neighbor modifications.
    private final ConcurrentHashMap<String, Set<String>> adj = new ConcurrentHashMap<>();

    /**
     * Represents an identified Bridge (Single Point of Failure) in the network.
     */
    public static class Bridge {
        private final String u;
        private final String v;
        private final Set<String> blastRadius;

        public Bridge(String u, String v, Set<String> blastRadius) {
            this.u = u;
            this.v = v;
            this.blastRadius = blastRadius;
        }

        public String getU() {
            return u;
        }

        public String getV() {
            return v;
        }

        public Set<String> getBlastRadius() {
            return blastRadius;
        }

        @Override
        public String toString() {
            return String.format("%s <-> %s (Blast Radius: %s)", u, v, blastRadius);
        }
    }

    /**
     * Adds an undirected edge between two service nodes.
     * Thread-safe, utilizing lock-free Map and Set compute utilities.
     */
    public void addEdge(String source, String target) {
        if (source == null || target == null || source.equals(target)) {
            return;
        }
        // Ensure both nodes are registered in the graph and mutually linked
        adj.computeIfAbsent(source, k -> ConcurrentHashMap.newKeySet()).add(target);
        adj.computeIfAbsent(target, k -> ConcurrentHashMap.newKeySet()).add(source);
    }

    /**
     * Removes the undirected edge between two service nodes.
     * Thread-safe.
     */
    public void removeEdge(String source, String target) {
        if (source == null || target == null) {
            return;
        }
        Set<String> sourceNeighbors = adj.get(source);
        if (sourceNeighbors != null) {
            sourceNeighbors.remove(target);
        }
        Set<String> targetNeighbors = adj.get(target);
        if (targetNeighbors != null) {
            targetNeighbors.remove(source);
        }
    }

    /**
     * Takes a point-in-time snapshot of the graph structure.
     * This isolates the algorithm execution from concurrent updates, avoiding
     * ConcurrentModificationExceptions or algorithm inconsistencies.
     *
     * @return A deep copy of the adjacency list.
     */
    public Map<String, Set<String>> getSnapshot() {
        Map<String, Set<String>> snapshot = new HashMap<>();
        for (Map.Entry<String, Set<String>> entry : adj.entrySet()) {
            // Snapshot only contains active connections
            if (!entry.getValue().isEmpty()) {
                snapshot.put(entry.getKey(), new HashSet<>(entry.getValue()));
            }
        }
        return snapshot;
    }

    // Helper class to hold running state of Tarjan's algorithm
    private static class TarjanState {
        int time = 0;
        final List<String> nodeList;
        final Map<String, Integer> nodeToIndex;
        final int[] disc;
        final int[] low;
        final boolean[] visited;
        final List<Bridge> bridges = new ArrayList<>();

        TarjanState(Set<String> allNodes) {
            this.nodeList = new ArrayList<>(allNodes);
            this.nodeToIndex = new HashMap<>();
            int n = nodeList.size();
            for (int i = 0; i < n; i++) {
                nodeToIndex.put(nodeList.get(i), i);
            }
            this.disc = new int[n];
            this.low = new int[n];
            this.visited = new boolean[n];
            Arrays.fill(disc, -1);
            Arrays.fill(low, -1);
        }
    }

    /**
     * Executes Tarjan's Bridge-Finding Algorithm on a point-in-time snapshot of the graph.
     * Time Complexity: O(V + E)
     *
     * Mathematical Intuition:
     * - A Depth First Search (DFS) tree is generated.
     * - "discoveryTime" (disc[u]) stores the order in which node 'u' is first visited.
     * - "low-link" (low[u]) represents the smallest discovery time reachable from 'u'
     *   using at most one back-edge (an edge to an ancestor in the DFS tree that is not its direct parent).
     *
     * When traversing edge (u, v) in the DFS tree:
     * - If 'v' has not been visited, we recursively run DFS on 'v'.
     * - Upon return, we update low[u] = min(low[u], low[v]) because any vertex reachable from 'v' is also reachable from 'u'.
     * - If low[v] > disc[u], it proves that there is no back-edge from any vertex in the subtree rooted at 'v'
     *   to 'u' or any of 'u's ancestors. Removing (u, v) would completely cut off 'v' and its descendants from 'u'.
     *   Thus, (u, v) is a Bridge.
     * - If 'v' has already been visited (and is not 'u's parent), it represents a back-edge. We update low[u] = min(low[u], disc[v]).
     *
     * @return List of identified bridges.
     */
    public List<Bridge> findBridges() {
        Map<String, Set<String>> snapshot = getSnapshot();

        // Find all unique vertices
        Set<String> allNodes = new HashSet<>();
        for (Map.Entry<String, Set<String>> entry : snapshot.entrySet()) {
            allNodes.add(entry.getKey());
            allNodes.addAll(entry.getValue());
        }

        if (allNodes.isEmpty()) {
            return Collections.emptyList();
        }

        TarjanState state = new TarjanState(allNodes);

        // Run DFS starting with 'frontend' first if available to prioritize it as root
        Integer frontendIndex = state.nodeToIndex.get("frontend");
        if (frontendIndex != null && !state.visited[frontendIndex]) {
            dfs(frontendIndex, -1, state, snapshot);
        }

        // Run DFS for any remaining unvisited nodes to handle disconnected components
        for (int i = 0; i < state.nodeList.size(); i++) {
            if (!state.visited[i]) {
                dfs(i, -1, state, snapshot);
            }
        }

        return state.bridges;
    }

    private void dfs(int u, int parent, TarjanState state, Map<String, Set<String>> graph) {
        state.visited[u] = true;
        state.time++;
        state.disc[u] = state.time;
        state.low[u] = state.time;

        String uName = state.nodeList.get(u);
        Set<String> neighbors = graph.getOrDefault(uName, Collections.emptySet());

        for (String vName : neighbors) {
            Integer vOpt = state.nodeToIndex.get(vName);
            if (vOpt == null) continue; // safety check
            int v = vOpt;

            if (v == parent) {
                continue; // Skip the back-edge to its parent in the DFS tree
            }

            if (!state.visited[v]) {
                // Recurse on unvisited neighbor
                dfs(v, u, state, graph);

                // On backtrack, update the low-link value of the parent
                state.low[u] = Math.min(state.low[u], state.low[v]);

                // Bridge Condition: Check if the subtree rooted at v has any path to an ancestor of u
                if (state.low[v] > state.disc[u]) {
                    // Edge (uName, vName) is a bridge!
                    // Blast radius: nodes that become isolated from the perspective of 'u' if the bridge drops.
                    // We run a simple reachability BFS on the graph snapshot from vName, forbidding traversal to uName.
                    Set<String> blastRadius = computeBlastRadius(vName, uName, graph);
                    state.bridges.add(new Bridge(uName, vName, blastRadius));
                }
            } else {
                // Back-edge: update low-link using the discovery time of the visited neighbor
                state.low[u] = Math.min(state.low[u], state.disc[v]);
            }
        }
    }

    /**
     * Calculates the blast radius when a bridge (u, v) is cut.
     * Computes the connected component starting from v, preventing traversal back to u.
     */
    private Set<String> computeBlastRadius(String startNode, String forbiddenNode, Map<String, Set<String>> graph) {
        Set<String> visited = new HashSet<>();
        Queue<String> queue = new LinkedList<>();

        queue.add(startNode);
        visited.add(startNode);

        while (!queue.isEmpty()) {
            String curr = queue.poll();
            Set<String> neighbors = graph.getOrDefault(curr, Collections.emptySet());
            for (String neighbor : neighbors) {
                // Prevent crossing back over the bridge link
                if ((curr.equals(startNode) && neighbor.equals(forbiddenNode)) ||
                    (curr.equals(forbiddenNode) && neighbor.equals(startNode))) {
                    continue;
                }
                if (!visited.contains(neighbor)) {
                    visited.add(neighbor);
                    queue.add(neighbor);
                }
            }
        }
        return visited;
    }
}
