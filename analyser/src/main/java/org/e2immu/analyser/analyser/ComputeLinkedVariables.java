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

import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analyser.util.WeightedGraph;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
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
or,
- clusters for LV 1 dependent, to update Context Modified
In this case, the return variable is never linked to any other variable.
 */
public class ComputeLinkedVariables {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComputeLinkedVariables.class);

    private final Stage stage;
    private final StatementAnalysis statementAnalysis;
    private final List<Cluster> clusters;
    private final WeightedGraph weightedGraph;


    private record Cluster(Set<Variable> variables, CausesOfDelay delays) {
    }

    private ComputeLinkedVariables(StatementAnalysis statementAnalysis,
                                   Stage stage,
                                   BiPredicate<VariableInfoContainer, Variable> ignore,
                                   WeightedGraph weightedGraph,
                                   List<Cluster> clusters) {
        this.clusters = clusters;
        this.stage = stage;
        this.statementAnalysis = statementAnalysis;
        this.weightedGraph = weightedGraph;
    }

    public static ComputeLinkedVariables create(StatementAnalysis statementAnalysis,
                                                Stage stage,
                                                boolean staticallyAssigned,
                                                BiPredicate<VariableInfoContainer, Variable> ignore,
                                                Set<Variable> reassigned,
                                                Function<Variable, LinkedVariables> externalLinkedVariables,
                                                EvaluationContext evaluationContext) {
        WeightedGraph weightedGraph = new WeightedGraph();
        Set<CauseOfDelay> delaysInClustering = new HashSet<>();
        // we keep track of all variables at the level, PLUS variables linked to, which are not at the level
        Set<Variable> variables = new HashSet<>();
        Set<Variable> atStage = new HashSet<>();
        statementAnalysis.variableEntryStream(stage).forEach(e -> {
            VariableInfoContainer vic = e.getValue();
            VariableInfo vi1 = vic.getPreviousOrInitial();
            Variable variable = vi1.variable();
            if (!ignore.test(vic, variable)) {
                variables.add(variable);
                atStage.add(variable);
                LinkedVariables curated = add(statementAnalysis, stage, staticallyAssigned, ignore, reassigned,
                        externalLinkedVariables, evaluationContext, weightedGraph, delaysInClustering, vi1, variable);
                variables.addAll(curated.variables().keySet());
            }
        });
        /*
        The code above should be sufficient, except that when a variable is linked to another one that is not in the stage,
        a delay in computeImmutableHiddenContent can go missing if the linking is computed one-sided (e.g., inMap:0,translationMap:3
        from one side, inMap:3,translationMap:0 from the other. The level 3 forces a check on computeImmutableHiddenContent,
        but if inMap is at E and translationMap only at C, not at E, than this side is not seen).
         */
        for (Variable variable : variables) {
            if (!atStage.contains(variable)) {
                // extra added, should we also process?
                VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(variable.fullyQualifiedName());
                if (vic != null && !ignore.test(vic, variable)) {
                    VariableInfo vi1 = vic.getPreviousOrInitial();
                    add(statementAnalysis, stage, staticallyAssigned, ignore, reassigned, externalLinkedVariables,
                            evaluationContext, weightedGraph, delaysInClustering, vi1, variable);
                }
            }
        }
        List<Cluster> clusters = computeClusters(weightedGraph, variables,
                staticallyAssigned ? LinkedVariables.STATICALLY_ASSIGNED_DV : DV.MIN_INT_DV,
                staticallyAssigned ? LinkedVariables.STATICALLY_ASSIGNED_DV : LinkedVariables.DEPENDENT_DV,
                !staticallyAssigned);

        // this will cause delays across the board for CONTEXT_ and EXTERNAL_ if the flag is set.
        if (evaluationContext.delayStatementBecauseOfECI()) {
            delaysInClustering.add(new SimpleCause(evaluationContext.getLocation(stage), CauseOfDelay.Cause.ECI_HELPER));
        }
        return new ComputeLinkedVariables(statementAnalysis, stage, ignore, weightedGraph, clusters);
    }

    private static LinkedVariables add(StatementAnalysis statementAnalysis,
                                       Stage stage,
                                       boolean staticallyAssigned,
                                       BiPredicate<VariableInfoContainer, Variable> ignore,
                                       Set<Variable> reassigned,
                                       Function<Variable, LinkedVariables> externalLinkedVariables,
                                       EvaluationContext evaluationContext,
                                       WeightedGraph weightedGraph,
                                       Set<CauseOfDelay> delaysInClustering,
                                       VariableInfo vi1,
                                       Variable variable) {
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
        LinkedVariables refToScope = variable instanceof FieldReference fr ? combined.merge(linkToScope(fr)) : combined;
        LinkedVariables curated = refToScope
                .removeIncompatibleWithImmutable(sourceImmutable, computeMyself, computeImmutable,
                        immutableCanBeIncreasedByTypeParameters, computeImmutableHiddenContent)
                .remove(v -> ignore.test(statementAnalysis.getVariableOrDefaultNull(v.fullyQualifiedName()), v));
        weightedGraph.addNode(variable, curated.variables(), true);
        boolean accountForDelay = staticallyAssigned || !(variable instanceof ReturnVariable);
        // context modified for the return variable is never linked, but the variables themselves must be present
        if (accountForDelay && curated.isDelayed()) {
            curated.variables().forEach((v, value) -> {
                if (value.isDelayed()) {
                    delaysInClustering.add(new VariableCause(v, statementAnalysis.location(stage), CauseOfDelay.Cause.LINKING));
                    value.causesOfDelay().causesStream().forEach(delaysInClustering::add);
                }
            });
        }
        return curated;
    }

    private static LinkedVariables linkToScope(FieldReference fr) {
        Set<Variable> variables = fr.scope.variablesWithoutCondition().stream()
                .filter(v -> !(v instanceof This))
                .collect(Collectors.toUnmodifiableSet());
        DV link = fr.scope.isDelayed() ? fr.scope.causesOfDelay() : LinkedVariables.DEPENDENT_DV;
        Map<Variable, DV> map = variables.stream().collect(Collectors.toUnmodifiableMap(v -> v, v -> link));
        return new LinkedVariables(map);
    }

    private static List<Cluster> computeClusters(WeightedGraph weightedGraph,
                                                 Set<Variable> variables,
                                                 DV minInclusive,
                                                 DV maxInclusive,
                                                 boolean followDelayed) {
        Set<Variable> done = new HashSet<>();
        List<Cluster> result = new ArrayList<>(variables.size());

        for (Variable variable : variables) {
            if (!done.contains(variable)) {
                Map<Variable, DV> map = weightedGraph.links(variable, maxInclusive, followDelayed);
                Set<Variable> reachable = map.entrySet().stream()
                        .filter(e -> e.getValue().ge(minInclusive) && e.getValue().le(maxInclusive))
                        .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
                DV delays = map.values().stream().reduce(DV.MIN_INT_DV, DV::max);
                Cluster cluster = new Cluster(reachable, delays == DV.MIN_INT_DV ? CausesOfDelay.EMPTY : delays.causesOfDelay());
                result.add(cluster);
                done.addAll(reachable);
            }
        }
        return result;
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
    private DV injectContextModifiedDelay(Variable variable, DV propertyValue, CausesOfDelay causesOfDelay) {
        if (causesOfDelay.containsCauseOfDelay(CauseOfDelay.Cause.CONTEXT_MODIFIED,
                c -> c instanceof VariableCause vc && vc.variable().equals(variable))) {
            return propertyValue;
        }
        CausesOfDelay specificDelay = DelayFactory.createDelay(new VariableCause(variable, statementAnalysis.location(stage),
                CauseOfDelay.Cause.CONTEXT_MODIFIED));
        return causesOfDelay.merge(specificDelay);
    }

    /**
     * break generated by ComputingParameterAnalyser, see FieldReference_3 as a primary example.
     * Cycle of 3 between a constructor parameter assigned to a field, with an accessor, and an instance method that calls the constructor.
     */
    private DV propertyValuePotentiallyBreakDelay(Property property, Variable v, DV propertyValue) {
        if (property == Property.CONTEXT_MODIFIED && propertyValue.isDelayed() &&
                propertyValue.containsCauseOfDelay(CauseOfDelay.Cause.BREAK_MOM_DELAY,
                        c -> c instanceof SimpleCause sc && sc.location().getInfo() instanceof ParameterInfo) ||
                // this second situation arises in InstanceOf_16: direct self reference
                propertyValue.containsCauseOfDelay(CauseOfDelay.Cause.CONTEXT_MODIFIED,
                        c -> c instanceof SimpleCause sc && sc.location().getInfo() == v)) {
            LOGGER.debug("Breaking a MOM / CM delay for parameter  in {}", propertyValue);
            return DV.FALSE_DV;
        }

        // normal action
        return propertyValue;
    }


    public CausesOfDelay write(Property property, Map<Variable, DV> propertyValues) {
        try {
            return writeProperty(property, propertyValues);
        } catch (IllegalStateException ise) {
            LOGGER.error("Clusters assigned are: {}", clusters);
            throw ise;
        }
    }

    private CausesOfDelay writeProperty(Property property, Map<Variable, DV> propertyValuesIn) {
        CausesOfDelay causes = CausesOfDelay.EMPTY;
        for (Cluster cluster : clusters) {
            Map<Variable, DV> propertyValues;
            /* context modified needs all linking to be done */
            if (Property.CONTEXT_MODIFIED == property) {
                CausesOfDelay clusterDelays = removeMyself(cluster.delays, cluster.variables);
                if (clusterDelays.isDelayed()) {
                    // a delay on clustering can be caused by a delay on the value to be linked
                    // replace @CM values by those delays, and inject a dedicated variable delay.
                    propertyValues = propertyValuesIn.entrySet().stream()
                            .filter(e -> cluster.variables.contains(e.getKey()))
                            .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey,
                                    e -> injectContextModifiedDelay(e.getKey(), e.getValue(), clusterDelays)));
                } else {
                    propertyValues = propertyValuesIn;
                }
            } else {
                // for all other properties, cluster delays are not taken into account (that agrees with the
                // choice for only STATICALLY_ASSIGNED)
                propertyValues = propertyValuesIn;
            }

            assert cluster.variables.stream().allMatch(propertyValues::containsKey);
            DV summary = cluster.variables.stream()
                    .map(v -> propertyValuePotentiallyBreakDelay(property, v, propertyValues.get(v)))
                    // IMPORTANT NOTE: falseValue gives 1 for IMMUTABLE and others, and sometimes we want the basis to be NOT_INVOLVED (0)
                    .reduce(DV.FALSE_DV, DV::max);
            if (summary.isDelayed()) {
                causes = causes.merge(summary.causesOfDelay());
            }
            for (Variable variable : cluster.variables) {
                VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(variable.fullyQualifiedName());
                if (vic != null) {
                    VariableInfo vi = vic.ensureLevelForPropertiesLinkedVariables(statementAnalysis.location(stage), stage);
                    DV current = vi.getProperty(property);
                    if (current.isDelayed()) {
                        try {
                            vic.setProperty(property, summary, stage);
                        } catch (IllegalStateException ise) {
                            LOGGER.error("Current cluster: {}", cluster);
                            throw ise;
                        }
                    } else if (summary.isDone() && !summary.equals(current)) {
                        LOGGER.error("Variable {} in cluster {}", variable, cluster.variables);
                        LOGGER.error("Property {}, current {}, new {}", property, current, summary);
                        throw new UnsupportedOperationException("Overwriting value");
                    }
                }
            }
        }
        return causes;
    }

    private CausesOfDelay removeMyself(CausesOfDelay delays, Set<Variable> variables) {
        Set<CauseOfDelay> causes = delays.causesStream()
                .filter(v -> v instanceof VariableCause vc && !variables.contains(vc.variable()) ||
                        !(v instanceof SimpleCause sc && sc.location().getInfo() instanceof ParameterInfo pi)
                        || !variables.contains(pi))
                .collect(Collectors.toUnmodifiableSet());
        return DelayFactory.createDelay(causes);
    }

    public Map<Variable, Set<Variable>> staticallyAssignedVariables() {
        // computed on the 0 values
        Map<Variable, Set<Variable>> staticallyAssigned = new HashMap<>();
        for (Cluster cluster : clusters) {
            for (Variable variable : cluster.variables) {
                Map<Variable, DV> map = weightedGraph.links(variable, null, true);
                Set<Variable> set = map.entrySet().stream()
                        // keep only statically assigned
                        .filter(e -> LinkedVariables.STATICALLY_ASSIGNED_DV.equals(e.getValue()))
                        // remove self-references
                        .filter(e -> !e.getKey().equals(variable))
                        .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
                staticallyAssigned.put(variable, set);
            }
        }
        return staticallyAssigned;
    }

    /**
     * This method differs from writeLinkedVariables in that it does not touch variables which do not
     * yet have an EVALUATION level. It is used in SAApply.
     * <p>
     * only used on the CM version (not statically assigned) with the statically assigned variables forming
     * the core.
     */
    public CausesOfDelay writeClusteredLinkedVariables(Map<Variable, Set<Variable>> staticallyAssignedVariables) {
        CausesOfDelay causes = CausesOfDelay.EMPTY;
        for (Cluster cluster : clusters) {
            for (Variable variable : cluster.variables) {
                VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(variable.fullyQualifiedName());
                assert vic != null : "No variable named " + variable.fullyQualifiedName();

                Map<Variable, DV> map = weightedGraph.links(variable, null, true);
                LinkedVariables linkedVariables = applyStaticallyAssignedAndRemoveSelfReference(staticallyAssignedVariables, variable, map);

                causes = causes.merge(linkedVariables.causesOfDelay());
                vic.ensureLevelForPropertiesLinkedVariables(statementAnalysis.location(stage), stage);
                try {
                    vic.setLinkedVariables(linkedVariables, stage);
                } catch (IllegalStateException isa) {
                    LOGGER.error("Linked variables change in illegal way in stmt {}: {}", statementAnalysis.index(), isa);
                    LOGGER.error("Variable: {}", variable);
                    LOGGER.error("Cluster : {}", cluster);
                    throw isa;
                }
            }
        }
        return causes;
    }

    private LinkedVariables applyStaticallyAssignedAndRemoveSelfReference(Map<Variable, Set<Variable>> staticallyAssignedVariables,
                                                                          Variable variable,
                                                                          Map<Variable, DV> map) {
        Set<Variable> staticallyAssigned = staticallyAssignedVariables.get(variable);
        if (staticallyAssigned != null) {
            staticallyAssigned.forEach(v -> map.put(v, LinkedVariables.STATICALLY_ASSIGNED_DV));
        }
        map.remove(variable); // no self references
        return map.isEmpty() ? LinkedVariables.EMPTY : new LinkedVariables(map);
    }

    /**
     * This variant is currently used by copyBackLocalCopies in StatementAnalysisImpl.
     * It touches all variables rather than those in clusters only.
     */
    public void writeLinkedVariables(Map<Variable, Set<Variable>> staticallyAssignedVariables,
                                     Set<Variable> touched,
                                     Set<Variable> toRemove) {
        assert stage == Stage.MERGE;
        statementAnalysis.rawVariableStream()
                .forEach(e -> {
                    VariableInfoContainer vic = e.getValue();

                    Variable variable = vic.current().variable();
                    if (touched.contains(variable)) {
                        Map<Variable, DV> map = weightedGraph.links(variable, null, true);
                        map.keySet().removeIf(toRemove::contains);
                        LinkedVariables linkedVariables = applyStaticallyAssignedAndRemoveSelfReference(staticallyAssignedVariables, variable, map);
                        vic.ensureLevelForPropertiesLinkedVariables(statementAnalysis.location(Stage.MERGE), Stage.MERGE);
                        vic.setLinkedVariables(linkedVariables, stage);
                    }
                });
    }

    /*
    clusters which contain parameters (or fields), get the CNN_TRAVELS_TO_PC flag set

    we have to try multiple times, because the nature of variableLinkedToLoopVariable(),
    which may connect one cluster to another, potentially not yet processed.
     */
    public void writeCnnTravelsToFields(boolean cnnTravelsToFields) {
        boolean change = true;
        while (change) {
            change = false;
            for (Cluster cluster : clusters) {
                boolean activate = cluster.variables.stream().anyMatch(v ->
                        !statementAnalysis.recursivelyLinkedToParameterOrField(v, cnnTravelsToFields).isEmpty());
                if (activate) {
                    for (Variable variable : cluster.variables) {
                        VariableInfoContainer vic = statementAnalysis.getVariable(variable.fullyQualifiedName());
                        DV current = vic.best(Stage.EVALUATION).getProperty(Property.CNN_TRAVELS_TO_PRECONDITION, null);
                        if (current == null) {
                            vic.setProperty(Property.CNN_TRAVELS_TO_PRECONDITION, DV.TRUE_DV, Stage.EVALUATION);
                            change = true;
                        }
                    }
                }
            }
        }
    }
}
