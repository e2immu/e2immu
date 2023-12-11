package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.CausesOfDelay;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.graph.op.DijkstraShortestPath;

import java.util.*;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.LinkedVariables.LINK_STATICALLY_ASSIGNED;

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

    ShortestPathImpl(Map<Variable, Integer> variableIndex,
                     Variable[] variables,
                     Map<Integer, Map<Integer, Long>> edges, CausesOfDelay someDelay) {
        this.variables = variables;
        this.edges = edges;
        this.variableIndex = variableIndex;
        this.someDelay = someDelay;
        dijkstraShortestPath = new DijkstraShortestPath(DijkstraShortestPath.JavaPriorityQueue::new);
    }


    private static final long DELAYED = 1L << 10;
    private static final long ASSIGNED = 1L << 20;
    private static final long DEPENDENT = 1L << 30;
    private static final long IS_HC_OF = 1L << 40;
    private static final long COMMON_HC = 1L << 50;

    public static long toDistanceComponent(DV dv) {
        if (LinkedVariables.LINK_STATICALLY_ASSIGNED.equals(dv)) return 1;
        if (dv.isDelayed()) return DELAYED;
        if (LinkedVariables.LINK_ASSIGNED.equals(dv)) return ASSIGNED;
        if (LinkedVariables.LINK_DEPENDENT.equals(dv)) return DEPENDENT;
        if (LinkedVariables.LINK_IS_HC_OF.equals(dv)) return IS_HC_OF;
        return COMMON_HC;
    }

    public static DV fromDistanceSum(long l, CausesOfDelay someDelay) {
        if (l < DELAYED) return LinkedVariables.LINK_STATICALLY_ASSIGNED;
        if (l < ASSIGNED) {
            assert someDelay != null && someDelay.isDelayed();
            return someDelay;
        }
        if (l < DEPENDENT) return LinkedVariables.LINK_ASSIGNED;
        if (l < IS_HC_OF) return LinkedVariables.LINK_DEPENDENT;
        if (l < COMMON_HC) return LinkedVariables.LINK_IS_HC_OF;
        return LinkedVariables.LINK_COMMON_HC;
    }


    @Override
    public Map<Variable, DV> links(Variable v, DV maxWeight, boolean followDelayed) {
        int startVertex = variableIndex.get(v);
        long maxWeightLong = maxWeight == null ? 0L : toDistanceComponent(maxWeight);
        DijkstraShortestPath.EdgeProvider edgeProvider = i -> {
            Map<Integer, Long> edgeMap = edges.get(i);
            if (edgeMap == null) return Stream.of();
            return edgeMap.entrySet().stream()
                    .filter(e -> (followDelayed || e.getValue() != DELAYED)
                            && (maxWeight == null || e.getValue() <= maxWeightLong));
        };
        long[] shortest = dijkstraShortestPath.shortestPath(variables.length, edgeProvider, startVertex);
        Map<Variable, DV> result = new HashMap<>();
        for (int j = 0; j < shortest.length; j++) {
            long d = shortest[j];
            if (d != Long.MAX_VALUE) {
                DV dv = fromDistanceSum(d, someDelay);
                result.put(variables[j], dv);
            }
        }
        return result;
    }

    /*
    Once we have followed a -3-> edge, we can follow <-4-> edges too.
     */

    @Override
    public Map<Variable, DV> linksFollowIsHCOf(Variable startingPoint, boolean followDelayed) {
        Map<Variable, DV> map = links(startingPoint, LinkedVariables.LINK_IS_HC_OF, followDelayed);
        List<Variable> toDo = new ArrayList<>();
        for (Map.Entry<Variable, DV> entry : map.entrySet()) {
            if (entry.getValue().equals(LinkedVariables.LINK_IS_HC_OF)) {
                toDo.add(entry.getKey());
            }
        }
        while (!toDo.isEmpty()) {
            Variable next = toDo.remove(0);
            Map<Variable, DV> mapNext = links(next, null, followDelayed);
            for (Map.Entry<Variable, DV> entry : mapNext.entrySet()) {
                Variable v = entry.getKey();
                if (!map.containsKey(v)) {
                    DV dv = entry.getValue();
                    if (dv.isDelayed() || dv.equals(LinkedVariables.LINK_COMMON_HC)) {
                        map.put(v, dv);
                    } else if (LinkedVariables.LINK_IS_HC_OF.equals(dv)) {
                        toDo.add(v);
                    }
                }
            }
        }
        return map;
    }
    // for testing

    Variable variablesGet(int i) {
        return variables[i];
    }
}
