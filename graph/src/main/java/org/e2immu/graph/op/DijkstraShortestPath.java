package org.e2immu.graph.op;

import java.util.Map;
import java.util.function.Supplier;

public class DijkstraShortestPath {
    public interface PriorityQueue {
        void add(int i, long priority);

        boolean isEmpty();

        int removeMin();

        void decreasePriority(int i, long priority);
    }

    private record QueueElement(int i, long priority) implements Comparable<QueueElement> {
        @Override
        public int compareTo(QueueElement o) {
            return Long.compare(priority, o.priority);
        }
    }

    public static class JavaPriorityQueue implements PriorityQueue {
        private final java.util.PriorityQueue<QueueElement> queue = new java.util.PriorityQueue<>();

        @Override
        public void add(int i, long priority) {
            queue.add(new QueueElement(i, priority));
        }

        @Override
        public boolean isEmpty() {
            return queue.isEmpty();
        }

        @Override
        public int removeMin() {
            QueueElement poll = queue.poll();
            assert poll != null;
            return poll.i;
        }

        @Override
        public void decreasePriority(int i, long priority) {
            queue.removeIf(qe -> i == qe.i); // linear time!
            queue.add(new QueueElement(i, priority)); // re-queue
        }
    }

    /*
    the vertices are numbered 0...n-1
     */
    public interface EdgeProvider {
        Iterable<Map.Entry<Integer, Long>> edges(int i);
    }

    /*
1  function Dijkstra(Graph, source):
2      dist[source] ← 0                           // Initialization
3
4      create vertex priority queue Q
5
6      for each vertex v in Graph.Vertices:
7          if v ≠ source
8              dist[v] ← INFINITY                 // Unknown distance from source to v
9              prev[v] ← UNDEFINED                // Predecessor of v
10
11         Q.add_with_priority(v, dist[v])
12
13
14     while Q is not empty:                      // The main loop
15         u ← Q.extract_min()                    // Remove and return best vertex
16         for each neighbor v of u:              // Go through all v neighbors of u
17             alt ← dist[u] + Graph.Edges(u, v)
18             if alt < dist[v]:
19                 dist[v] ← alt
20                 prev[v] ← u
21                 Q.decrease_priority(v, alt)
22
23     return dist, prev
     */

    private final Supplier<PriorityQueue> priorityQueueSupplier;

    public DijkstraShortestPath(Supplier<PriorityQueue> priorityQueueSupplier) {
        this.priorityQueueSupplier = priorityQueueSupplier;
    }

    public long[] shortestPath(int numVertices, EdgeProvider edgeProvider, int sourceVertex) {
        long[] dist = new long[numVertices]; // dist[source]<-0

        PriorityQueue priorityQueue = priorityQueueSupplier.get();
        for (int i = 0; i < numVertices; i++) {
            if (i != sourceVertex) {
                dist[i] = Long.MAX_VALUE;
            }
            priorityQueue.add(i, dist[i]);
        }
        while (!priorityQueue.isEmpty()) {
            int u = priorityQueue.removeMin();
            for (Map.Entry<Integer, Long> edge : edgeProvider.edges(u)) {
                long d = dist[u];
                long alt = d == Long.MAX_VALUE ? Long.MAX_VALUE : d + edge.getValue();
                int v = edge.getKey();
                if (alt < dist[v]) {
                    dist[v] = alt;
                    priorityQueue.decreasePriority(v, alt);
                }
            }
        }
        return dist;
    }
}
