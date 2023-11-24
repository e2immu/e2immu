package org.e2immu.graph.op;

import org.e2immu.graph.G;
import org.e2immu.graph.V;

import java.util.*;

public class Common {

    public static <T> Set<V<T>> follow(G<T> g, V<T> startingPoint) {
        assert startingPoint != null;
        List<V<T>> toDo = new LinkedList<>();
        Set<V<T>> connected = new LinkedHashSet<>();
        toDo.add(startingPoint);
        connected.add(startingPoint);
        while (!toDo.isEmpty()) {
            V<T> v = toDo.remove(0);
            Map<V<T>, Long> edges = g.edges(v);
            if (edges != null) {
                for (V<T> to : edges.keySet()) {
                    if (connected.add(to)) {
                        toDo.add(to);
                    }
                }
            }
        }
        return connected;
    }
}
