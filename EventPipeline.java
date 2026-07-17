package resiliencylens;

import java.util.concurrent.BlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Central Processing Pipeline.
 * Continuously consumes serialized telemetry event strings from the queue,
 * parses them, and updates the thread-safe NetworkGraph representation.
 */
public class EventPipeline implements Runnable {

    // Regex pattern to extract: action (CONNECT/DISCONNECT), source node, and target node
    private static final Pattern EVENT_PATTERN = Pattern.compile(
        "\\[EVENT\\]\\s+(CONNECT|DISCONNECT)\\s*\\|\\s*source=([^\\s|]+)\\s*\\|\\s*target=([^\\s|]+)"
    );

    // ANSI Escape Codes for CLI dashboard formatting
    private static final String RESET = "\u001B[0m";
    private static final String CYAN = "\u001B[36m";
    private static final String GREEN = "\u001B[32m";
    private static final String RED = "\u001B[31m";
    private static final String WHITE_BOLD = "\u001B[1;37m";

    private final BlockingQueue<String> eventQueue;
    private final NetworkGraph graph;
    private volatile boolean running = true;

    public EventPipeline(BlockingQueue<String> eventQueue, NetworkGraph graph) {
        this.eventQueue = eventQueue;
        this.graph = graph;
    }

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                // Blocks until an event becomes available
                String eventStr = eventQueue.take();
                processEvent(eventStr);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    /**
     * Parses the incoming raw telemetry string and applies updates to the NetworkGraph.
     */
    private void processEvent(String eventStr) {
        if (eventStr == null) return;

        Matcher matcher = EVENT_PATTERN.matcher(eventStr);
        if (matcher.find()) {
            String action = matcher.group(1);
            String source = matcher.group(2);
            String target = matcher.group(3);

            if ("CONNECT".equalsIgnoreCase(action)) {
                graph.addEdge(source, target);
                logInboundUpdate(true, source, target);
            } else if ("DISCONNECT".equalsIgnoreCase(action)) {
                graph.removeEdge(source, target);
                logInboundUpdate(false, source, target);
            }
        } else {
            System.err.println("[PIPELINE ERROR] Malformed telemetry event string: " + eventStr);
        }
    }

    private void logInboundUpdate(boolean connected, String source, String target) {
        String actionSymbol = connected ? GREEN + "➕ CONNECT   " + RESET : RED + "➖ DISCONNECT" + RESET;
        String edgeColor = WHITE_BOLD;
        System.out.printf("%s[INBOUND]%s %s | %s%s%s <-> %s%s%s\n",
            CYAN, RESET, actionSymbol, edgeColor, source, RESET, edgeColor, target, RESET);
    }
}
