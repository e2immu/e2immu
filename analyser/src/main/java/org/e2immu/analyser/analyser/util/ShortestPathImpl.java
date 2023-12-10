package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.model.variable.Variable;

import java.util.*;

public class ShortestPathImpl implements ShortestPath {
    private final Map<Variable, Integer> variableIndex;
    private final Variable[] variables;
    private final DV[][] distances;

    ShortestPathImpl(Map<Variable, Integer> variableIndex, Variable[] variables, DV[][] distances) {
        this.variables = variables;
        this.distances = distances;
        this.variableIndex = variableIndex;
    }

    /*
    Basic Dijkstra algorithm.
    The maxValue is taken independently of the delay.
     */
    private DV[] compute(int start, DV maxValue, boolean followDelayed) {
        int V = variables.length;
        DV[] dist = new DV[V];
        boolean[] done = new boolean[V];
        dist[start] = LinkedVariables.LINK_STATICALLY_ASSIGNED;

        for (int count = 0; count < V - 1; count++) {
            int u = minDistance(dist, done);
            done[u] = true;
            for (int v = 0; v < V; v++) {
                DV d = distances[u][v];
                if (!done[v] && d != null && (maxValue == null
                        || followDelayed && d.isDelayed() || le(d, maxValue)) && dist[u] != null) {
                    DV sum = sum(dist[u], d);
                    if (dist[v] == null || lt(sum, dist[v])) {
                        dist[v] = sum;
                    }
                }
            }
        }
        return dist;
    }

    private boolean lt(DV d1, DV d2) {
        if (d1.equals(d2)) return false;
        if (d1.isDelayed()) return false;
        if (d2.isDelayed()) return true;
        return d1.lt(d2);
    }

    private boolean le(DV d1, DV d2) {
        if (d1.equals(d2)) return true;
        if (d1.isDelayed()) return false;
        if (d2.isDelayed()) return true;
        return d1.le(d2);
    }

    private DV sum(DV d1, DV d2) {
        if (d1.isDelayed()) return d1;
        if (d2.isDelayed()) return d2;
        return d1.max(d2);
    }

    private int minDistance(DV[] distance, boolean[] done) {
        DV min = null;
        int minIndex = -1;
        for (int v = 0; v < done.length; v++) {
            if (!done[v] && (min == null || distance[v] != null && le(distance[v], min))) {
                min = distance[v];
                minIndex = v;
            }
        }
        return minIndex;
    }


    @Override
    public Map<Variable, DV> links(Variable v, DV maxWeight, boolean followDelayed) {
        int i = variableIndex.get(v);
        DV[] shortest = compute(i, maxWeight, followDelayed);
        Map<Variable, DV> result = new HashMap<>();
        for (int j = 0; j < shortest.length; j++) {
            DV d = shortest[j];
            if (d != null && (followDelayed || !d.isDelayed())) {
                result.put(variables[j], d);
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
