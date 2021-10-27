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
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.util.WeightedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.function.Predicate;

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
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeLinkedVariables.class);

    private final VariableInfoContainer.Level level;
    private final StatementAnalysis statementAnalysis;
    private final List<List<Variable>> clustersAssigned;
    private final List<List<Variable>> clustersDependent;
    public final boolean delaysInClustering;
    private final WeightedGraph<Variable> weightedGraph;
    private final Predicate<Variable> ignore;

    private ComputeLinkedVariables(StatementAnalysis statementAnalysis,
                                   VariableInfoContainer.Level level,
                                   Predicate<Variable> ignore,
                                   WeightedGraph<Variable> weightedGraph,
                                   List<List<Variable>> clustersAssigned,
                                   List<List<Variable>> clustersDependent,
                                   boolean delaysInClustering) {
        this.level = level;
        this.statementAnalysis = statementAnalysis;
        this.clustersAssigned = clustersAssigned;
        this.clustersDependent = clustersDependent;
        this.delaysInClustering = delaysInClustering;
        this.weightedGraph = weightedGraph;
        this.ignore = ignore;
    }

    public static ComputeLinkedVariables create(StatementAnalysis statementAnalysis,
                                                VariableInfoContainer.Level level,
                                                Predicate<Variable> ignore,
                                                Set<Variable> reassigned,
                                                Function<Variable, LinkedVariables> externalLinkedVariables,
                                                AnalysisProvider analysisProvider) {
        WeightedGraph<Variable> weightedGraph = new WeightedGraph<>();
        AtomicBoolean delaysInClustering = new AtomicBoolean();
        List<Variable> variables = new ArrayList<>(statementAnalysis.variables.size());

        statementAnalysis.variables.stream().forEach(e -> {
            VariableInfoContainer vic = e.getValue();
            VariableInfo vi1 = vic.getPreviousOrInitial();
            Variable variable = vi1.variable();
            if (!ignore.test(variable)) {
                variables.add(variable);

                Function<Variable, Integer> computeImmutable = v -> v instanceof This ? MultiLevel.NOT_INVOLVED :
                        v.parameterizedType().defaultImmutable(analysisProvider, false);
                int sourceImmutable = computeImmutable.apply(variable);
                boolean isBeingReassigned = reassigned.contains(variable);

                LinkedVariables external = externalLinkedVariables.apply(variable);
                LinkedVariables inVi = isBeingReassigned ? LinkedVariables.EMPTY
                        : vi1.getLinkedVariables().remove(reassigned);
                LinkedVariables combined = external.merge(inVi);
                LinkedVariables curated = combined
                        .removeIncompatibleWithImmutable(sourceImmutable, computeImmutable)
                        .remove(ignore);

                boolean bidirectional = vic.variableNature().localCopyOf() == null;
                weightedGraph.addNode(variable, curated.variables(), bidirectional);
                if (curated.isDelayed()) delaysInClustering.set(true);
            }
        });

        List<List<Variable>> clustersAssigned = computeClusters(weightedGraph, variables, LinkedVariables.ASSIGNED);
        List<List<Variable>> clustersDependent = computeClusters(weightedGraph, variables, LinkedVariables.DEPENDENT);
        return new ComputeLinkedVariables(statementAnalysis, level, ignore, weightedGraph, clustersAssigned,
                clustersDependent, delaysInClustering.get());
    }

    private static List<List<Variable>> computeClusters(WeightedGraph<Variable> weightedGraph,
                                                        List<Variable> variables,
                                                        int dependent) {
        Set<Variable> done = new HashSet<>();
        List<List<Variable>> result = new ArrayList<>(variables.size());

        for (Variable variable : variables) {
            if (!done.contains(variable)) {
                Map<Variable, Integer> map = weightedGraph.links(variable, false);
                List<Variable> reachable = map.entrySet().stream()
                        .filter(e -> e.getValue() > LinkedVariables.DELAYED_VALUE && e.getValue() <= dependent)
                        .map(Map.Entry::getKey).toList();
                result.add(reachable);
                done.addAll(reachable);
            }
        }
        return result;
    }

    public AnalysisStatus write(VariableProperty property, Map<Variable, Integer> propertyValues) {
        if (VariableProperty.CONTEXT_MODIFIED == property) {
            if (delaysInClustering) return AnalysisStatus.DELAYS;
            return writeProperty(clustersDependent, property, propertyValues);
        }
        // there cannot be a delay on assignments
        return writeProperty(clustersAssigned, property, propertyValues);
    }

    private AnalysisStatus writeProperty(List<List<Variable>> clusters,
                                         VariableProperty variableProperty,
                                         Map<Variable, Integer> propertyValues) {
        AnalysisStatus analysisStatus = AnalysisStatus.DONE;
        for (List<Variable> cluster : clusters) {
            int summary = computeSummary(cluster, propertyValues);
            if (summary == Level.DELAY) {
                analysisStatus = AnalysisStatus.DELAYS;
            } else {
                for (Variable variable : cluster) {
                    VariableInfoContainer vic = statementAnalysis.variables.getOrDefaultNull(variable.fullyQualifiedName());
                    if(vic != null) {
                        ensureLevel(vic);
                        try {
                            vic.setProperty(variableProperty, summary, level);
                        } catch (IllegalStateException ise) {
                            LOGGER.error("Current cluster: {}", cluster);
                            throw ise;
                        }
                    }
                }
            }
        }
        return analysisStatus;
    }

    private void ensureLevel(VariableInfoContainer vic) {
        if (!vic.has(level)) {
            vic.ensureLevelForPropertiesLinkedVariables(level);
        }
    }

    // IMPORTANT NOTE: falseValue gives 1 for IMMUTABLE and others, and sometimes we want the basis to be NOT_INVOLVED (0)
    private int computeSummary(List<Variable> cluster, Map<Variable, Integer> propertyValues) {
        return cluster.stream()
                .mapToInt(v -> propertyValues.getOrDefault(v, Level.DELAY))
                .reduce(0, Level.OR);
    }

    public void writeLinkedVariables() {
        statementAnalysis.variables.stream().forEach(e -> {
            VariableInfoContainer vic = e.getValue();

            Variable variable = vic.current().variable();
            if (!ignore.test(variable)) {
                Map<Variable, Integer> map = weightedGraph.links(variable, true);
                LinkedVariables linkedVariables = map.isEmpty() ? LinkedVariables.EMPTY : new LinkedVariables(map);

                ensureLevel(vic);
                vic.setLinkedVariables(linkedVariables, level);
            }
        });
    }
}
