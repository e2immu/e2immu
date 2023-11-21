package org.e2immu.graph;

import java.util.Map;

public interface EdgePrinter<T> {

    String print(Map<V<T>, Map<V<T>, Long>> edges);

}
