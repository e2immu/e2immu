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
import org.e2immu.analyser.model.TypeAnalysis;
import org.e2immu.analyser.model.TypeInfo;
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
    private final List<List<Variable>> clustersStaticallyAssigned;
    private final List<List<Variable>> clustersDependent;
    public final boolean delaysInClustering;
    private final WeightedGraph<Variable> weightedGraph;
    private final Predicate<Variable> ignore;

    private ComputeLinkedVariables(StatementAnalysis statementAnalysis,
                                   VariableInfoContainer.Level level,
                                   Predicate<Variable> ignore,
                                   WeightedGraph<Variable> weightedGraph,
                                   List<List<Variable>> clustersStaticallyAssigned,
                                   List<List<Variable>> clustersDependent,
                                   boolean delaysInClustering) {
        this.clustersStaticallyAssigned = clustersStaticallyAssigned;
        this.clustersDependent = clustersDependent;
        this.delaysInClustering = delaysInClustering;
        this.ignore = ignore;
        this.level = level;
        this.statementAnalysis = statementAnalysis;
        this.weightedGraph = weightedGraph;
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

        statementAnalysis.variableEntryStream(level).forEach(e -> {
            VariableInfoContainer vic = e.getValue();
            VariableInfo vi1 = vic.getPreviousOrInitial();
            Variable variable = vi1.variable();
            if (!ignore.test(variable)) {
                variables.add(variable);

                Function<Variable, Integer> computeImmutable = v -> v instanceof This ? MultiLevel.NOT_INVOLVED :
                        v.parameterizedType().defaultImmutable(analysisProvider, false);
                Function<Variable, Integer> computeImmutableHiddenContent = v -> v instanceof This ? MultiLevel.NOT_INVOLVED :
                        v.parameterizedType().immutableOfHiddenContent(analysisProvider, true);
                Function<Variable, Boolean> immutableCanBeIncreasedByTypeParameters = v -> {
                    TypeInfo bestType = v.parameterizedType().bestTypeInfo();
                    if (bestType == null) return true;
                    TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysis(bestType);
                    return typeAnalysis.immutableCanBeIncreasedByTypeParameters();
                };

                int sourceImmutable = computeImmutable.apply(variable);
                boolean isBeingReassigned = reassigned.contains(variable);

                LinkedVariables external = externalLinkedVariables.apply(variable);
                LinkedVariables inVi = isBeingReassigned ? LinkedVariables.EMPTY
                        : vi1.getLinkedVariables().remove(reassigned);
                LinkedVariables combined = external.merge(inVi);
                LinkedVariables curated = combined
                        .removeIncompatibleWithImmutable(sourceImmutable, computeImmutable,
                                immutableCanBeIncreasedByTypeParameters, computeImmutableHiddenContent)
                        .remove(ignore);

                boolean bidirectional = vic.variableNature().localCopyOf() == null;
                weightedGraph.addNode(variable, curated.variables(), bidirectional);
                if (curated.isDelayed()) delaysInClustering.set(true);
            }
        });

        List<List<Variable>> clustersAssigned = computeClusters(weightedGraph, variables,
                LinkedVariables.STATICALLY_ASSIGNED, LinkedVariables.STATICALLY_ASSIGNED);
        List<List<Variable>> clustersDependent = computeClusters(weightedGraph, variables,
                LinkedVariables.DELAYED_VALUE, LinkedVariables.DEPENDENT);
        return new ComputeLinkedVariables(statementAnalysis, level, ignore, weightedGraph, clustersAssigned,
                clustersDependent, delaysInClustering.get());
    }

    private static List<List<Variable>> computeClusters(WeightedGraph<Variable> weightedGraph,
                                                        List<Variable> variables,
                                                        int minInclusive,
                                                        int maxInclusive) {
        Set<Variable> done = new HashSet<>();
        List<List<Variable>> result = new ArrayList<>(variables.size());

        for (Variable variable : variables) {
            if (!done.contains(variable)) {
                Map<Variable, Integer> map = weightedGraph.links(variable, false);
                List<Variable> reachable = map.entrySet().stream()
                        .filter(e -> e.getValue() >= minInclusive && e.getValue() <= maxInclusive)
                        .map(Map.Entry::getKey).toList();
                result.add(reachable);
                done.addAll(reachable);
            }
        }
        return result;
    }

    public AnalysisStatus write(VariableProperty property, Map<Variable, Integer> propertyValues) {
        if (delaysInClustering && VariableProperty.CONTEXT_NOT_NULL != property) {
            return AnalysisStatus.DELAYS;
        }
        if (VariableProperty.CONTEXT_MODIFIED == property) {
            return writeProperty(clustersDependent, property, propertyValues);
        }
        /* only CNN will immediately write, because the ENN of fields is needed to compute values of fields,
         which in turn are needed to get rid of delays.
         */
        try {
            return writeProperty(clustersStaticallyAssigned, property, propertyValues);
        } catch (IllegalStateException ise) {
            LOGGER.error("Clusters assigned are: {}", clustersStaticallyAssigned);
            throw ise;
        }
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
                    if (vic != null) {
                        VariableInfo vi = vic.ensureLevelForPropertiesLinkedVariables(level);
                        if (vi.getProperty(variableProperty) == Level.DELAY) {
                            try {
                                vic.setProperty(variableProperty, summary, level);
                            } catch (IllegalStateException ise) {
                                LOGGER.error("Current cluster: {}", cluster);
                                throw ise;
                            }
                        } /*
                         else: local copies make it hard to get all values on the same line
                         The principle is: once it gets a value, that's the one it keeps
                         */
                    }
                }
            }
        }
        return analysisStatus;
    }


    // IMPORTANT NOTE: falseValue gives 1 for IMMUTABLE and others, and sometimes we want the basis to be NOT_INVOLVED (0)
    private int computeSummary(List<Variable> cluster, Map<Variable, Integer> propertyValues) {
        return cluster.stream()
                .mapToInt(v -> propertyValues.getOrDefault(v, Level.DELAY))
                .reduce(0, Level.OR);
    }

    public void writeLinkedVariables() {
        statementAnalysis.variableEntryStream(level)
                .forEach(e -> {
                    VariableInfoContainer vic = e.getValue();

                    Variable variable = vic.current().variable();
                    if (!ignore.test(variable)) {
                        Map<Variable, Integer> map = weightedGraph.links(variable, true);
                        LinkedVariables linkedVariables = map.isEmpty() ? LinkedVariables.EMPTY : new LinkedVariables(map);

                        vic.ensureLevelForPropertiesLinkedVariables(level);
                        vic.setLinkedVariables(linkedVariables, level);
                    }
                });
    }
}
