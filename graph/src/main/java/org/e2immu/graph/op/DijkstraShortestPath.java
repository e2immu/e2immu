package org.e2immu.graph.op;

import org.jheaps.AddressableHeap;
import org.jheaps.tree.PairingHeap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class DijkstraShortestPath {

    public record DC(long dist, ConnectInfo ci) implements Comparable<DC> {
        @Override
        public int compareTo(DC o) {
            return Long.compare(dist, o.dist);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof DC dc && dist == dc.dist;
        }
    }

    public static class DCEntry implements Map.Entry<Integer, DijkstraShortestPath.DC> {
        int variable;
        DC dc;

        public DCEntry(Map.Entry<Integer, Long> e) {
            variable = e.getKey();
            dc = new DC(e.getValue(), null);
        }

        public DCEntry(int variable, DC dc) {
            this.variable = variable;
            this.dc = dc;
        }

        @Override
        public Integer getKey() {
            return variable;
        }

        @Override
        public DijkstraShortestPath.DC getValue() {
            return dc;
        }

        @Override
        public DijkstraShortestPath.DC setValue(DijkstraShortestPath.DC value) {
            throw new UnsupportedOperationException();
        }
    }

    /*
    the vertices are numbered 0...n-1
     */
    public interface EdgeProvider {
        Stream<Map.Entry<Integer, DC>> edges(int i);
    }

    public interface ConnectInfoProvider {
        ConnectInfo connectInfo(int i);
    }

    public interface ConnectInfo {
        boolean accept(ConnectInfo other);
    }

    /* from wikipedia:

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


    private static final DC NO_PATH = new DC(Long.MAX_VALUE, null);

    public long[] shortestPath(int numVertices, EdgeProvider edgeProvider, int sourceVertex) {
        DC[] dcs = shortestPath(numVertices, edgeProvider, i -> null, sourceVertex);
        return Arrays.stream(dcs).mapToLong(DC::dist).toArray();
    }

    public DC[] shortestPath(int numVertices,
                             EdgeProvider edgeProvider,
                             ConnectInfoProvider connectInfoProvider,
                             int sourceVertex) {
        DC[] dist = new DC[numVertices]; // dist[source]<-0 implicit

        // https://en.wikipedia.org/wiki/Priority_queue
        // current implementation from org.jheaps library, recursively included in JGraphT
        // https://en.wikipedia.org/wiki/Pairing_heap,
        PairingHeap<DC, Integer> priorityQueue = new PairingHeap<>(); // default comparator
        List<AddressableHeap.Handle<DC, Integer>> handles = new ArrayList<>(numVertices);

        for (int i = 0; i < numVertices; i++) {
            DC dc;
            if (i != sourceVertex) {
                dc = NO_PATH;
            } else {
                dc = new DC(0, connectInfoProvider.connectInfo(i));
            }
            dist[i] = dc;
            handles.add(priorityQueue.insert(dc, i));
        }
        while (!priorityQueue.isEmpty()) {
            int u = priorityQueue.deleteMin().getValue();
            edgeProvider.edges(u).forEach(edge -> {
                DC d = dist[u];
                DC alt;
                if (d == NO_PATH) {
                    alt = NO_PATH;
                } else {
                    DC edgeValue = edge.getValue();
                    if (edgeValue.ci == null) {
                        ConnectInfo ci = connectInfoProvider.connectInfo(u);
                        alt = new DC(d.dist + edgeValue.dist, ci);
                    } else {
                        boolean accept = d.ci.accept(edgeValue.ci);
                        if (accept) {
                            alt = new DC(d.dist + edgeValue.dist, edgeValue.ci);
                        } else {
                            alt = NO_PATH;
                        }
                    }
                }

                int v = edge.getKey();
                if (alt.dist < dist[v].dist) {
                    dist[v] = alt;
                    AddressableHeap.Handle<DC, Integer> handle = handles.get(v);
                    handle.decreaseKey(alt);
                }
            });
        }
        return dist;
    }
}
