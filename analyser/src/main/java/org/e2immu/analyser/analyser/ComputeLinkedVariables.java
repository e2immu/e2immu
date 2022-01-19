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

import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.util.WeightedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
    public final CausesOfDelay delaysInClustering;
    private final WeightedGraph<Variable, DV> weightedGraph;
    private final BiPredicate<VariableInfoContainer, Variable> ignore;

    private ComputeLinkedVariables(StatementAnalysis statementAnalysis,
                                   VariableInfoContainer.Level level,
                                   BiPredicate<VariableInfoContainer, Variable> ignore,
                                   WeightedGraph<Variable, DV> weightedGraph,
                                   List<List<Variable>> clustersStaticallyAssigned,
                                   List<List<Variable>> clustersDependent,
                                   CausesOfDelay delaysInClustering) {
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
                                                BiPredicate<VariableInfoContainer, Variable> ignore,
                                                Set<Variable> reassigned,
                                                Function<Variable, LinkedVariables> externalLinkedVariables,
                                                EvaluationContext evaluationContext) {
        WeightedGraph<Variable, DV> weightedGraph = new WeightedGraph<>(() -> LinkedVariables.STATICALLY_ASSIGNED_DV);
        Set<CauseOfDelay> delaysInClustering = new HashSet<>();
        // we keep track of all variables at the level, PLUS variables linked to, which are not at the level
        Set<Variable> variables = new HashSet<>();

        statementAnalysis.variableEntryStream(level).forEach(e -> {
            VariableInfoContainer vic = e.getValue();
            VariableInfo vi1 = vic.getPreviousOrInitial();
            Variable variable = vi1.variable();
            if (!ignore.test(vic, variable)) {
                variables.add(variable);

                AnalysisProvider analysisProvider = evaluationContext.getAnalyserContext();
                Predicate<Variable> computeMyself = evaluationContext::isMyself;
                Function<Variable, DV> computeImmutable = v -> v instanceof This || evaluationContext.isMyself(v) ? MultiLevel.NOT_INVOLVED_DV :
                        analysisProvider.defaultImmutable(v.parameterizedType(), false);
                Function<Variable, DV> computeImmutableHiddenContent = v -> v instanceof This ? MultiLevel.NOT_INVOLVED_DV :
                        analysisProvider.immutableOfHiddenContent(v.parameterizedType(), true);
                Function<Variable, DV> immutableCanBeIncreasedByTypeParameters = v -> {
                    TypeInfo bestType = v.parameterizedType().bestTypeInfo();
                    if (bestType == null) return DV.TRUE_DV;
                    TypeAnalysis typeAnalysis = analysisProvider.getTypeAnalysis(bestType);
                    return typeAnalysis.immutableCanBeIncreasedByTypeParameters();
                };

                DV sourceImmutable = computeImmutable.apply(variable);
                boolean isBeingReassigned = reassigned.contains(variable);

                LinkedVariables external = externalLinkedVariables.apply(variable);
                LinkedVariables inVi = isBeingReassigned ? LinkedVariables.EMPTY
                        : vi1.getLinkedVariables().remove(reassigned);
                LinkedVariables combined = external.merge(inVi);
                LinkedVariables curated = combined
                        .removeIncompatibleWithImmutable(sourceImmutable, computeMyself, computeImmutable,
                                immutableCanBeIncreasedByTypeParameters, computeImmutableHiddenContent)
                        .remove(v -> ignore.test(statementAnalysis.getVariableOrDefaultNull(v.fullyQualifiedName()), v));

                weightedGraph.addNode(variable, curated.variables(), true);
                if (curated.isDelayed()) {
                    curated.variables().forEach((v, value) -> {
                        if (value.isDelayed()) {
                            delaysInClustering.add(new VariableCause(v, statementAnalysis.location(), CauseOfDelay.Cause.LINKING));
                            value.causesOfDelay().causesStream().forEach(delaysInClustering::add);
                        }
                    });
                }
                variables.addAll(curated.variables().keySet());
            }
        });

        List<List<Variable>> clustersAssigned = computeClusters(weightedGraph, variables,
                LinkedVariables.STATICALLY_ASSIGNED_DV, LinkedVariables.STATICALLY_ASSIGNED_DV);
        List<List<Variable>> clustersDependent = computeClusters(weightedGraph, variables,
                DV.MIN_INT_DV, LinkedVariables.DEPENDENT_DV);
        return new ComputeLinkedVariables(statementAnalysis, level, ignore, weightedGraph, clustersAssigned,
                clustersDependent, SimpleSet.from(delaysInClustering));
    }

    private static List<List<Variable>> computeClusters(WeightedGraph<Variable, DV> weightedGraph,
                                                        Set<Variable> variables,
                                                        DV minInclusive,
                                                        DV maxInclusive) {
        Set<Variable> done = new HashSet<>();
        List<List<Variable>> result = new ArrayList<>(variables.size());

        for (Variable variable : variables) {
            if (!done.contains(variable)) {
                Map<Variable, DV> map = weightedGraph.links(variable, false);
                List<Variable> reachable = map.entrySet().stream()
                        .filter(e -> e.getValue().ge(minInclusive) && e.getValue().le(maxInclusive))
                        .map(Map.Entry::getKey).toList();
                result.add(reachable);
                done.addAll(reachable);
            }
        }
        return result;
    }

    public CausesOfDelay write(Property property, Map<Variable, DV> propertyValues) {
        /* context modified needs all linking to be done */
        if (Property.CONTEXT_MODIFIED == property) {
            if (delaysInClustering.isDelayed()) {
                // a delay on clustering can be caused by a delay on the value to be linked
                // replace @CM values by those delays, and inject a dedicated variable delay.
                Map<Variable, DV> delayedValues = propertyValues.entrySet().stream()
                        .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                                e -> injectContextModifiedDelay(e.getKey(), e.getValue())));
                return writeProperty(clustersDependent, property, delayedValues);
            }
            return writeProperty(clustersDependent, property, propertyValues);
        }
        /* all other context properties can be written based on statically assigned values */
        try {
            return writeProperty(clustersStaticallyAssigned, property, propertyValues);
        } catch (IllegalStateException ise) {
            LOGGER.error("Clusters assigned are: {}", clustersStaticallyAssigned);
            throw ise;
        }
    }

    /**
     * See Basics_20 for the necessity to delay CM when the clustering of linked variables produced delays.
     * See Modification_19 for an example where breaking the cyclic dependency is necessary.
     *
     * @param variable      the variable for which we are computing the CM property
     * @param propertyValue the value of the CM property
     * @return in normal situation, the delaysInClustering augmented with a specific variable delay; if this delay is
     * already present, we detect a cyclic dependency, and do not inject a delay. Rather, we return the CM property value.
     */
    private DV injectContextModifiedDelay(Variable variable, DV propertyValue) {
        if (delaysInClustering.causesStream().anyMatch(c -> c.cause() == CauseOfDelay.Cause.CONTEXT_MODIFIED &&
                c instanceof VariableCause vc && vc.variable().equals(variable))) {
            return propertyValue;
        }
        CausesOfDelay specificDelay = new SimpleSet(new VariableCause(variable, statementAnalysis.location(),
                CauseOfDelay.Cause.CONTEXT_MODIFIED));
        return delaysInClustering.causesOfDelay().merge(specificDelay);
    }

    private CausesOfDelay writeProperty(List<List<Variable>> clusters,
                                        Property property,
                                        Map<Variable, DV> propertyValues) {
        CausesOfDelay causes = CausesOfDelay.EMPTY;
        for (List<Variable> cluster : clusters) {
            DV summary = cluster.stream()
                    // IMPORTANT: property has to be present, or we get a null pointer!
                    .map(propertyValues::get)
                    // IMPORTANT NOTE: falseValue gives 1 for IMMUTABLE and others, and sometimes we want the basis to be NOT_INVOLVED (0)
                    .reduce(DV.FALSE_DV, DV::max);
            if (summary.isDelayed()) {
                causes = causes.merge(summary.causesOfDelay());
            }
            for (Variable variable : cluster) {
                VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(variable.fullyQualifiedName());
                if (vic != null) {
                    VariableInfo vi = vic.ensureLevelForPropertiesLinkedVariables(statementAnalysis.location(), level);
                    if (vi.getProperty(property).isDelayed()) {
                        try {
                            vic.setProperty(property, summary, level);
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
        return causes;
    }

    /**
     * This method differs from writeLinkedVariables in that it does not touch variables which do not
     * yet have an EVALUATION level. It is used in SAApply.
     */
    public void writeClusteredLinkedVariables() {
        for (List<Variable> cluster : clustersStaticallyAssigned) {
            for (Variable variable : cluster) {
                VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(variable.fullyQualifiedName());
                assert vic != null : "No variable named " + variable.fullyQualifiedName();

                Map<Variable, DV> map = weightedGraph.links(variable, true);
                LinkedVariables linkedVariables = map.isEmpty() ? LinkedVariables.EMPTY : new LinkedVariables(map);

                vic.ensureLevelForPropertiesLinkedVariables(statementAnalysis.location(), level);
                vic.setLinkedVariables(linkedVariables, level);
            }
        }
    }

    /**
     * This variant is currently used by copyBackLocalCopies in StatementAnalysisImpl.
     * It touches all variables rather than those in clusters only.
     */
    public void writeLinkedVariables(Set<Variable> touched, Set<Variable> toRemove) {
        assert level == VariableInfoContainer.Level.MERGE;
        statementAnalysis.rawVariableStream()
                .forEach(e -> {
                    VariableInfoContainer vic = e.getValue();

                    Variable variable = vic.current().variable();
                    if (touched.contains(variable)) {
                        Map<Variable, DV> map = weightedGraph.links(variable, true);
                        map.keySet().removeIf(toRemove::contains);

                        LinkedVariables linkedVariables = map.isEmpty() ? LinkedVariables.EMPTY : new LinkedVariables(map);

                        vic.ensureLevelForPropertiesLinkedVariables(statementAnalysis.location(), VariableInfoContainer.Level.MERGE);
                        vic.setLinkedVariables(linkedVariables, level);
                    }
                });
    }
}
