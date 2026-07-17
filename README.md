# ResiliencyLens

ResiliencyLens is a real-time **Network Topology & Vulnerability Monitoring Engine** written in pure Java (JDK 17+). It dynamically tracks network updates from service telemetry logs, builds an in-memory representation of the microservice topology, and scans it in real-time to alert on structural **Single Points of Failure (SPOFs)**.

The engine implements **Tarjan's Bridge-Finding Algorithm** from scratch to find critical links that, if severed, would partition the network. It also calculates the **blast radius** (isolated downstream components) for each vulnerability.

---

## Features

- **No Framework Dependencies**: Built using pure standard JDK 17+ libraries (`java.util.concurrent` primitives, regex, and console I/O).
- **High-Throughput Thread-Safe Adjacency List**: Uses `ConcurrentHashMap` with lock-free concurrent sets to manage real-time edge additions and removals.
- **Snapshot Isolation**: Copies the graph state for analysis to keep Tarjan's DFS runs free of data races or concurrent modification exceptions.
- **Tarjan's Bridge-Finding Algorithm**: Linear-time $O(V + E)$ scanning of network topology structures.
- **Dynamic Blast Radius Analysis**: Runs BFS to evaluate exactly which services lose reachability to the ingress gateway when a bridge link drops.
- **Interactive DevOps CLI Dashboard**: Features custom log formatting, status screens, and warning block modules using ANSI styling.

---


## Quick Start

Ensure you have **JDK 17 or higher** installed.

### 1. Compile the Source Code
Compile all Java source files from the `src` directory:
```bash
# Navigate to src folder
cd src

# Compile source files to bin folder
mkdir bin 2>/dev/null
javac -d bin resiliencylens/*.java
```

### 2. Run Algorithmic Verification Tests
Verify the mathematical correctness of Tarjan's bridge-finding implementation across standard test graphs:
```bash
java -cp bin resiliencylens.TarjanTest
```

### 3. Run the Live Monitor Dashboard
Launch the engine with the telemetry simulator and processing pipeline active:
```bash
java -cp bin resiliencylens.Main
```
*Press `Ctrl + C` in the terminal to trigger the clean shutdown sequence and exit.*

---

## Mathematical Intuition (Tarjan's Algorithm)

The engine monitors nodes in a DFS traversal and calculates:
- `discoveryTime[u]`: the step order index when service `u` is first visited.
- `low[u]`: the lowest discovery time reachable from `u` through at most one back-edge.

If a child node `v` has `low[v] > discoveryTime[u]`, it proves there is no alternative path from `v` (or any of its descendants) to any ancestor of `u`. Thus, the link `u <-> v` is a critical bridge.
