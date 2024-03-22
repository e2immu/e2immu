package org.e2immu.graph.op;

import org.jheaps.AddressableHeap;
import org.jheaps.tree.PairingHeap;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class DijkstraShortestPath {

    private final Connection initialConnection;

    public interface Connection {
        boolean doesNotContain(Connection required);
    }

    public record DC(long dist, Connection connection) implements Comparable<DC> {
        @Override
        public int compareTo(DC o) {
            return Long.compare(dist, o.dist);
        }

        @Override
        public boolean equals(Object obj) {
            return obj instanceof DC dc && dist == dc.dist;
        }
    }

    public record DCP(long dist, Connection mine, Connection theirs) {
        public Connection accept(Connection connection) {
            if (mine == null) {
                // no check, always true
                return connection;
            }
            if (connection.doesNotContain(mine)) {
                return null;
            }
            return theirs;
        }
    }

    public static class DCPEntry implements Map.Entry<Integer, DijkstraShortestPath.DCP> {
        int variable;
        DCP dcp;

        public DCPEntry(Map.Entry<Integer, Long> e) {
            variable = e.getKey();
            dcp = new DCP(e.getValue(), null, null);
        }

        public DCPEntry(int variable, DCP distanceAndConnectionPattern) {
            this.variable = variable;
            this.dcp = distanceAndConnectionPattern;
        }

        @Override
        public Integer getKey() {
            return variable;
        }

        @Override
        public DijkstraShortestPath.DCP getValue() {
            return dcp;
        }

        @Override
        public DijkstraShortestPath.DCP setValue(DijkstraShortestPath.DCP value) {
            throw new UnsupportedOperationException();
        }
    }

    /*
    the vertices are numbered 0...n-1
     */
    public interface EdgeProvider {
        Stream<Map.Entry<Integer, DCP>> edges(int i);
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


    public DijkstraShortestPath(Connection initialConnection) {
        this.initialConnection = initialConnection;
    }

    public DijkstraShortestPath() {
        this(required -> false);
    }

    public long[] shortestPath(int numVertices, EdgeProvider edgeProvider, int sourceVertex) {
        DC[] dcs = shortestPathDC(numVertices, edgeProvider, sourceVertex);
        return Arrays.stream(dcs).mapToLong(DC::dist).toArray();
    }

    public DC[] shortestPathDC(int numVertices,
                               EdgeProvider edgeProvider,
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
                dc = new DC(0, initialConnection);
            }
            dist[i] = dc;
            handles.add(priorityQueue.insert(dc, i));
        }
        while (!priorityQueue.isEmpty()) {
            int u = priorityQueue.deleteMin().getValue();

            edgeProvider.edges(u).forEach(edge -> {
                int v = edge.getKey();

                DC d = dist[u];
                DC alt;
                if (d == NO_PATH) {
                    alt = NO_PATH;
                } else {
                    DCP edgeValue = edge.getValue();
                    Connection next = edgeValue.accept(d.connection);
                    if (next == null) {
                        alt = NO_PATH;
                    } else {
                        alt = new DC(d.dist + edgeValue.dist, next);
                    }
                }
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
