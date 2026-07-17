package resiliencylens;

import java.util.*;
import java.util.concurrent.BlockingQueue;

/**
 * Live Telemetry Simulator that runs in the background.
 * Periodically generates realistic, structured network topology events.
 */
public class TelemetrySimulator implements Runnable {

    // Predefined logical relationships between microservices
    private static final String[][] SYSTEM_TOPOLOGY_TEMPLATE = {
        {"frontend", "api-gateway"},
        {"api-gateway", "auth-service"},
        {"api-gateway", "payment-service"},
        {"api-gateway", "inventory-service"},
        {"api-gateway", "recommendation-service"},
        {"payment-service", "order-db"},
        {"inventory-service", "order-db"},
        {"recommendation-service", "analytics-db"},
        {"auth-service", "order-db"},             // Closes a cycle: api-gateway -> auth-service -> order-db -> payment-service -> api-gateway
        {"payment-service", "inventory-service"} // Closes a cycle: api-gateway -> payment-service -> inventory-service -> api-gateway
    };

    private final BlockingQueue<String> eventQueue;
    private final Set<String> activeEdges = new HashSet<>();
    private final Random random = new Random();
    private volatile boolean running = true;

    public TelemetrySimulator(BlockingQueue<String> eventQueue) {
        this.eventQueue = eventQueue;
        // Bootstrap the graph with some initial connections to avoid starting empty
        bootstrapInitialTopology();
    }

    private void bootstrapInitialTopology() {
        // Initial setup has a few critical links (some of which are bridges)
        connectEdge("frontend", "api-gateway");
        connectEdge("api-gateway", "auth-service");
        connectEdge("api-gateway", "payment-service");
        connectEdge("payment-service", "order-db");
    }

    private void connectEdge(String u, String v) {
        String key = getEdgeKey(u, v);
        activeEdges.add(key);
        // Push initial events to queue
        eventQueue.offer(formatEvent("CONNECT", u, v));
    }

    private String getEdgeKey(String u, String v) {
        return u.compareTo(v) < 0 ? u + "<->" + v : v + "<->" + u;
    }

    private String formatEvent(String action, String source, String target) {
        return String.format("[EVENT] %s | source=%s | target=%s", action, source, target);
    }

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // Sleep for a randomized duration between 2 to 3 seconds as requested
                long sleepTime = 2000 + random.nextInt(1001);
                Thread.sleep(sleepTime);

                simulateEvent();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Randomly generates a CONNECT or DISCONNECT event based on current topology density.
     */
    private void simulateEvent() {
        // Decide connection vs disconnection probability based on active edges count
        // We want to keep active edges between 3 and 8 to ensure meaningful topological changes
        boolean shouldConnect;
        if (activeEdges.size() <= 3) {
            shouldConnect = true;
        } else if (activeEdges.size() >= 8) {
            shouldConnect = false;
        } else {
            shouldConnect = random.nextDouble() < 0.6; // Slightly favor connecting to build paths
        }

        int attempt = 0;
        while (attempt < 10) {
            // Pick a random edge template
            int index = random.nextInt(SYSTEM_TOPOLOGY_TEMPLATE.length);
            String u = SYSTEM_TOPOLOGY_TEMPLATE[index][0];
            String v = SYSTEM_TOPOLOGY_TEMPLATE[index][1];
            String key = getEdgeKey(u, v);

            if (shouldConnect) {
                if (!activeEdges.contains(key)) {
                    activeEdges.add(key);
                    eventQueue.offer(formatEvent("CONNECT", u, v));
                    break;
                }
            } else {
                // Prevent disconnecting essential entry links completely if it would empty the graph
                // (e.g. keep at least some services active)
                if (activeEdges.contains(key)) {
                    activeEdges.remove(key);
                    eventQueue.offer(formatEvent("DISCONNECT", u, v));
                    break;
                }
            }
            attempt++;
        }
    }
}
