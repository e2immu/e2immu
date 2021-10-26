/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.util.WeightedGraph;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/*
Goal:

In:
- a list of variable info's, at evaluation level
- their current linkedVariables are up-to-date
- changes to these LVs from the EvaluationResult

Out:
- VI's linked variables updated

Hold:
- clusters for LV 0 assigned, to update properties
- clusters for LV 1 dependent, to update Context Modified

 */
public class ComputeLinkedVariables {

    private final VariableInfoContainer.Level level;
    private final StatementAnalysis statementAnalysis;
    private final List<List<Variable>> clustersAssigned;
    private final List<List<Variable>> clustersDependent;
    public final boolean delaysInClustering;

    // public for testing
    public ComputeLinkedVariables(StatementAnalysis statementAnalysis,
                                  VariableInfoContainer.Level level,
                                  List<List<Variable>> clustersAssigned,
                                  List<List<Variable>> clustersDependent,
                                  boolean delaysInClustering) {
        this.level = level;
        this.statementAnalysis = statementAnalysis;
        this.clustersAssigned = clustersAssigned;
        this.clustersDependent = clustersDependent;
        this.delaysInClustering = delaysInClustering;
    }

    public static ComputeLinkedVariables create(StatementAnalysis statementAnalysis,
                                                VariableInfoContainer.Level level,
                                                EvaluationResult evaluationResult,
                                                AnalysisProvider analysisProvider) {
        WeightedGraph<Variable> weightedGraph = new WeightedGraph<>();
        AtomicBoolean delaysInClustering = new AtomicBoolean();
        List<Variable> variables = new ArrayList<>(statementAnalysis.variables.size());

        statementAnalysis.variables.stream().forEach(e -> {
            VariableInfoContainer vic = e.getValue();
            VariableInfo vi = vic.best(level);
            Variable variable = vi.variable();
            variables.add(variable);
            int immutable = variable.parameterizedType().defaultImmutable(analysisProvider, true);
            if (immutable == Level.DELAY) delaysInClustering.set(true);

            EvaluationResult.ChangeData changeData = evaluationResult.changeData().get(variable);

            LinkedVariables inCd = changeData.linkedVariables();
            LinkedVariables inVi = vi.getLinkedVariables();
            LinkedVariables combined = inCd.merge(inVi);
            LinkedVariables curated = combined.removeIncompatibleWithImmutable(immutable);

            weightedGraph.addNode(variable, curated.variables(), true);
            if (curated.isDelayed()) delaysInClustering.set(true);
        });

        List<List<Variable>> clustersAssigned = computeClusters(weightedGraph, variables, LinkedVariables.ASSIGNED);
        List<List<Variable>> clustersDependent = computeClusters(weightedGraph, variables, LinkedVariables.DEPENDENT);
        return new ComputeLinkedVariables(statementAnalysis, level, clustersAssigned,
                clustersDependent, delaysInClustering.get());
    }

    private static List<List<Variable>> computeClusters(WeightedGraph<Variable> weightedGraph,
                                                        List<Variable> variables,
                                                        int dependent) {
        Set<Variable> done = new HashSet<>();
        List<List<Variable>> result = new ArrayList<>(variables.size());

        for (Variable variable : variables) {
            if (!done.contains(variable)) {
                Map<Variable, Integer> map = weightedGraph.links(variable);
                List<Variable> reachable = map.entrySet().stream()
                        .filter(e -> e.getValue() > LinkedVariables.DELAYED_VALUE && e.getValue() <= dependent)
                        .map(Map.Entry::getKey).toList();
                result.add(reachable);
                done.addAll(reachable);
            }
        }
        return result;
    }

    public AnalysisStatus writeProperties(GroupPropertyValues groupPropertyValues) {
        return GroupPropertyValues.PROPERTIES.stream()
                .map(vp -> write(vp, groupPropertyValues.getMap(vp)))
                .reduce(AnalysisStatus.DONE, AnalysisStatus::combine);
    }

    public AnalysisStatus write(VariableProperty property, Map<Variable, Integer> propertyValues) {
        if (VariableProperty.CONTEXT_MODIFIED == property) {
            return writeProperty(clustersDependent, property, propertyValues);
        }
        return writeProperty(clustersAssigned, property, propertyValues);
    }

    private AnalysisStatus writeProperty(List<List<Variable>> clusters,
                                         VariableProperty variableProperty,
                                         Map<Variable, Integer> propertyValues) {
        AnalysisStatus analysisStatus = AnalysisStatus.DONE;
        int counter = 0;
        for (List<Variable> cluster : clusters) {
            int summary = computeSummary(cluster, propertyValues);
            if (summary == Level.DELAY) {
                analysisStatus = AnalysisStatus.DELAYS;
            } else {
                for (Variable variable : cluster) {
                    VariableInfoContainer vic = statementAnalysis.variables.get(variable.fullyQualifiedName());
                    vic.setProperty(variableProperty, summary, level);
                    counter++;
                }
            }
        }
        assert counter == propertyValues.keySet().size();

        return analysisStatus;
    }

    private int computeSummary(List<Variable> cluster, Map<Variable, Integer> propertyValues) {
        return cluster.stream().mapToInt(propertyValues::get).reduce(Level.DELAY, Math::max);
    }
}
