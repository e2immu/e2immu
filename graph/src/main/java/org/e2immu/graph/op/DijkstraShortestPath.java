package org.e2immu.graph.op;

import org.jheaps.AddressableHeap;
import org.jheaps.tree.PairingHeap;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class DijkstraShortestPath {

    /*
    the vertices are numbered 0...n-1
     */
    public interface EdgeProvider {
        Stream<Map.Entry<Integer, Long>> edges(int i);
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

    public long[] shortestPath(int numVertices, EdgeProvider edgeProvider, int sourceVertex) {
        long[] dist = new long[numVertices]; // dist[source]<-0 implicit

        PairingHeap<Long, Integer> priorityQueue = new PairingHeap<>(); // default comparator
        List<AddressableHeap.Handle<Long, Integer>> handles = new ArrayList<>(numVertices);

        for (int i = 0; i < numVertices; i++) {
            if (i != sourceVertex) {
                dist[i] = Long.MAX_VALUE;
            }
            handles.add(priorityQueue.insert(dist[i], i));
        }
        while (!priorityQueue.isEmpty()) {
            int u = priorityQueue.deleteMin().getValue();
            edgeProvider.edges(u).forEach(edge -> {
                long d = dist[u];
                long alt = d == Long.MAX_VALUE ? Long.MAX_VALUE : d + edge.getValue();
                int v = edge.getKey();
                if (alt < dist[v]) {
                    dist[v] = alt;
                    AddressableHeap.Handle<Long, Integer> handle = handles.get(v);
                    handle.decreaseKey(alt);
                }
            });
        }
        return dist;
    }
}
