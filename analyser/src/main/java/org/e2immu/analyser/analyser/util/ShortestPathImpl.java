package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.model.variable.Variable;

import java.util.HashMap;
import java.util.Map;

public class ShortestPathImpl implements ShortestPath {
    private final Map<Variable, Integer> variableIndex;
    private final Variable[] variables;
    private final DV[][] distances;

    ShortestPathImpl(Map<Variable, Integer> variableIndex, Variable[] variables, DV[][] distances) {
        this.variables = variables;
        this.distances = distances;
        this.variableIndex = variableIndex;
    }

    private DV[] compute(int start, DV maxValue) {
        int V = variables.length;
        DV[] dist = new DV[V];
        boolean[] done = new boolean[V];
        dist[start] = LinkedVariables.LINK_STATICALLY_ASSIGNED;

        for (int count = 0; count < V - 1; count++) {
            int u = minDistance(dist, done);
            done[u] = true;
            for (int v = 0; v < V; v++) {
                DV d = distances[u][v];
                if (!done[v] && d != null && (maxValue == null || d.le(maxValue)) && dist[u] != null) {
                    DV sum = sum(dist[u], d);
                    if (dist[v] == null || sum.lt(dist[v])) {
                        dist[v] = sum;
                    }
                }
            }
        }
        return dist;
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
            if (!done[v] && (min == null || distance[v] != null && distance[v].le(min))) {
                min = distance[v];
                minIndex = v;
            }
        }
        return minIndex;
    }


    @Override
    public Map<Variable, DV> links(Variable v, DV maxWeight, boolean followDelayed) {
        int i = variableIndex.get(v);
        DV[] shortest = compute(i, maxWeight);
        Map<Variable, DV> result = new HashMap<>();
        for (int j = 0; j < shortest.length; j++) {
            DV d = shortest[j];
            if (d != null && (followDelayed || !d.isDelayed())) {
                result.put(variables[j], d);
            }
        }
        return result;
    }

    // for testing

    Variable variablesGet(int i) {
        return variables[i];
    }
}
