package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.LV;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.delay.NoDelay;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.graph.op.DijkstraShortestPath;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.LV.*;

/*
Note: the comparator and the sum work according to different order of the values.
I have no idea if Dijkstra's algorithm is compatible with this.
Tests look OK for now.
 */
public class ShortestPathImpl implements ShortestPath {
    private final Map<Variable, Integer> variableIndex;
    private final Variable[] variables;
    private final Map<Integer, Map<Integer, Long>> edges;
    private final CausesOfDelay someDelay;
    private final DijkstraShortestPath dijkstraShortestPath;
    private final LinkMap linkMap;

    ShortestPathImpl(Map<Variable, Integer> variableIndex,
                     Variable[] variables,
                     Map<Integer, Map<Integer, Long>> edges,
                     CausesOfDelay someDelay,
                     LinkMap linkMap) {
        this.variables = variables;
        this.edges = edges;
        this.variableIndex = variableIndex;
        this.someDelay = someDelay;
        dijkstraShortestPath = new DijkstraShortestPath();
        this.linkMap = linkMap;
    }

    private static final int BITS = 12;

    private static final long STATICALLY_ASSIGNED = 1L;
    private static final long DELAYED = 1L << BITS;
    private static final long ASSIGNED = 1L << (2 * BITS);
    private static final long DEPENDENT = 1L << (3 * BITS);
    private static final long INDEPENDENT_HC = 1L << (4 * BITS);

    public static long toDistanceComponent(LV dv) {
        if (LINK_STATICALLY_ASSIGNED.equals(dv)) return STATICALLY_ASSIGNED;
        if (dv.isDelayed()) return DELAYED;
        if (LINK_ASSIGNED.equals(dv)) return ASSIGNED;
        if (LINK_DEPENDENT.equals(dv)) return DEPENDENT;
        assert dv.isCommonHC();
        return INDEPENDENT_HC;
    }

    // used to produce the result.
    public static LV fromDistanceSum(long l, CausesOfDelay someDelay) {
        if (l < DELAYED) return LINK_STATICALLY_ASSIGNED;
        if (((l >> BITS) & (DELAYED - 1)) > 0) {
            assert someDelay != null && someDelay.isDelayed();
            return LV.delay(someDelay);
        }
        if (l < DEPENDENT) return LINK_ASSIGNED;
        if (l < INDEPENDENT_HC) return LINK_DEPENDENT;
        return LINK_COMMON_HC;
    }

    public static char code(LV dv) {
        if (dv.isDelayed()) return 'D';
        return (char) ((int) '0' + dv.value());
    }

    record Key(int start, long maxWeight) {
    }

    record LinkMap(Map<Key, long[]> map, AtomicInteger savingsCount) implements Cache.CacheElement {
        @Override
        public int savings() {
            return savingsCount.get();
        }
    }


    @Override
    public Map<Variable, LV> links(Variable v, LV maxWeight) {
        int startVertex = variableIndex.get(v);
        long maxWeightLong = maxWeight == null ? 0L : toDistanceComponent(maxWeight);
        Key key = new Key(startVertex, maxWeightLong);
        long[] inMap = linkMap.map.get(key);
        long[] shortest;
        if (inMap != null) {
            shortest = inMap;
            linkMap.savingsCount.incrementAndGet();
        } else {
            DijkstraShortestPath.EdgeProvider edgeProvider = i -> {
                Map<Integer, Long> edgeMap = edges.get(i);
                if (edgeMap == null) return Stream.of();
                return edgeMap.entrySet().stream()
                        .filter(e -> maxWeight == null || e.getValue() <= maxWeightLong);
            };
            shortest = dijkstraShortestPath.shortestPath(variables.length, edgeProvider, startVertex);
            linkMap.map.put(key, shortest);
            linkMap.savingsCount.decrementAndGet();
        }
        Map<Variable, LV> result = new HashMap<>();
        for (int j = 0; j < shortest.length; j++) {
            long d = shortest[j];
            if (d != Long.MAX_VALUE) {
                LV dv = fromDistanceSum(d, someDelay);
                result.put(variables[j], dv);
            }
        }
        return result;
    }

    // for testing

    Variable variablesGet(int i) {
        return variables[i];
    }
}
