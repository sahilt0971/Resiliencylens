package resiliencylens;

import java.util.*;
import java.util.concurrent.*;

/**
 * Real-Time Operational Logging Dashboard & Main Orchestrator.
 * Handles the lifecycle of the simulator, processing pipeline, and periodic scheduler.
 */
public class Main {

    // ANSI Escape Codes for DevOps style logging
    private static final String RESET = "\u001B[0m";
    private static final String RED = "\u001B[31m";
    private static final String GREEN = "\u001B[32m";
    private static final String YELLOW = "\u001B[33m";
    private static final String BLUE = "\u001B[34m";
    private static final String CYAN = "\u001B[36m";
    private static final String WHITE_BOLD = "\u001B[1;37m";
    private static final String RED_BOLD = "\u001B[1;31m";
    private static final String GREEN_BOLD = "\u001B[1;32m";
    private static final String YELLOW_BOLD = "\u001B[1;33m";
    private static final String CYAN_BOLD = "\u001B[1;36m";

    public static void main(String[] args) {
        printBanner();

        // Initialize shared concurrency constructs
        LinkedBlockingQueue<String> eventQueue = new LinkedBlockingQueue<>();
        NetworkGraph graph = new NetworkGraph();

        // Spin up background telemetry simulator thread
        TelemetrySimulator simulator = new TelemetrySimulator(eventQueue);
        Thread simulatorThread = new Thread(simulator, "TelemetrySimulator-Thread");
        simulatorThread.setDaemon(true);

        // Spin up central processing pipeline thread
        EventPipeline pipeline = new EventPipeline(eventQueue, graph);
        Thread pipelineThread = new Thread(pipeline, "EventPipeline-Thread");
        pipelineThread.setDaemon(true);

        System.out.println(CYAN + "[SYSTEM] Launching Live Telemetry Simulator..." + RESET);
        simulatorThread.start();

        System.out.println(CYAN + "[SYSTEM] Launching Event Processing Pipeline..." + RESET);
        pipelineThread.start();

        // Setup the periodic engine analyzer
        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "TopologyScan-Thread");
            t.setDaemon(true);
            return t;
        });

        System.out.println(CYAN + "[SYSTEM] Scheduling Vulnerability Scanning Engine (every 5 seconds)..." + RESET);
        System.out.println(GREEN + "[SYSTEM] ResiliencyLens Engine is running. Press Ctrl+C to stop.\n" + RESET);

        scheduler.scheduleAtFixedRate(() -> {
            try {
                System.out.println("\n" + CYAN_BOLD + "[ENGINE] 🔍 Scanning network topology..." + RESET);
                long startTime = System.nanoTime();

                List<NetworkGraph.Bridge> bridges = graph.findBridges();

                long durationMs = (System.nanoTime() - startTime) / 1_000_000;
                System.out.printf("[ENGINE] Scan completed in %d ms.\n", durationMs);

                Map<String, Set<String>> snapshot = graph.getSnapshot();
                int connCount = countConnections(snapshot);

                System.out.printf("[STATUS] Monitored services: %s%s%s | Active links: %s%d%s\n",
                    WHITE_BOLD, snapshot.keySet(), RESET, WHITE_BOLD, connCount, RESET);

                if (bridges.isEmpty()) {
                    System.out.printf("[STATUS] %s🟢 Topology Healthy: No Single Points of Failure (SPOFs) detected.%s\n",
                        GREEN_BOLD, RESET);
                } else {
                    System.out.printf("[STATUS] %s⚠️  VULNERABILITY ALERT: %d Single Point(s) of Failure detected!%s\n",
                        YELLOW_BOLD, bridges.size(), RESET);

                    for (NetworkGraph.Bridge bridge : bridges) {
                        printBridgeWarning(bridge);
                    }
                }
            } catch (Exception e) {
                System.err.println("[ENGINE ERROR] Exception during scanning loop: " + e.getMessage());
                e.printStackTrace();
            }
        }, 5, 5, TimeUnit.SECONDS);

        // Add a clean shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\n" + RED + "[SYSTEM] Initiating clean shutdown sequence..." + RESET);
            simulator.stop();
            pipeline.stop();
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
            }
            System.out.println(GREEN + "[SYSTEM] Shutdown complete. ResiliencyLens terminated safely." + RESET);
        }, "Shutdown-Hook"));

        // Keep the main thread alive (since other threads are daemons)
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            System.out.println("[SYSTEM] Main orchestrator interrupted.");
        }
    }

    private static void printBanner() {
        System.out.println(CYAN + "================================================================================" + RESET);
        System.out.println(WHITE_BOLD + "           ██████╗ ███████╗███████╗██╗██╗     ██╗███████╗███╗   ██╗███████╗" + RESET);
        System.out.println(WHITE_BOLD + "           ██╔══██╗██╔════╝██╔════╝██║██║     ██║██╔════╝████╗  ██║██╔════╝" + RESET);
        System.out.println(WHITE_BOLD + "           ██████╔╝█████╗  ███████╗██║██║     ██║█████╗  ██╔██╗ ██║███████╗" + RESET);
        System.out.println(WHITE_BOLD + "           ██╔══██╗██╔══╝  ╚════██║██║██║     ██║██╔══╝  ██║╚██╗██║╚════██║" + RESET);
        System.out.println(WHITE_BOLD + "           ██║  ██║███████╗███████║██║███████╗██║███████╗██║ ╚████║███████║" + RESET);
        System.out.println(WHITE_BOLD + "           ╚═╝  ╚═╝╚══════╝╚══════╝╚═╝╚══════╝╚═╝╚══════╝╚═╝  ╚═══╝╚══════╝" + RESET);
        System.out.println(CYAN + "                   Network Topology & Vulnerability Monitoring Engine" + RESET);
        System.out.println(CYAN + "================================================================================" + RESET);
    }

    private static void printBridgeWarning(NetworkGraph.Bridge bridge) {
        String u = bridge.getU();
        String v = bridge.getV();
        Set<String> blast = bridge.getBlastRadius();

        // Calculate maximum string length for formatting dynamic space
        int width = 78;
        String lineHeader = RED + "│" + RESET;

        System.out.println(RED + "┌──────────────────────────────────────────────────────────────────────────────┐" + RESET);
        System.out.println(RED + "│" + RED_BOLD + "  ⚠️  CRITICAL VULNERABILITY: SINGLE POINT OF FAILURE DETECTED                 " + RED + "│" + RESET);
        System.out.println(RED + "├──────────────────────────────────────────────────────────────────────────────┤" + RESET);
        System.out.printf("%s  Link:         %s%s <-> %s%s\n", lineHeader, WHITE_BOLD, u, v, RESET);
        System.out.printf("%s  Severity:     %sHIGH%s (Structural Cut Link)\n", lineHeader, RED_BOLD, RESET);
        System.out.printf("%s  Blast Radius: %s%s%s node(s) isolated\n", lineHeader, YELLOW_BOLD, blast.size(), RESET);
        System.out.printf("%s                Isolated Component: %s%s%s\n", lineHeader, YELLOW, blast, RESET);
        System.out.println(RED + "└──────────────────────────────────────────────────────────────────────────────┘" + RESET);
    }

    private static int countConnections(Map<String, Set<String>> snapshot) {
        int count = 0;
        for (Set<String> neighbors : snapshot.values()) {
            count += neighbors.size();
        }
        return count / 2;
    }
}
