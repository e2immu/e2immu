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

import org.e2immu.analyser.analyser.delay.ProgressAndDelay;
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
import org.e2immu.support.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
    private final Cluster returnValueCluster;
    private final Variable returnVariable;
    private final WeightedGraph weightedGraph;
    private final boolean allowBreakDelay;
    private final Set<Variable> linkingNotYetSet;

    private record Cluster(Set<Variable> variables, CausesOfDelay delays) {
        @Override
        public String toString() {
            return "[" +
                    "variables=" + variables.stream().map(Variable::simpleName).sorted().collect(Collectors.joining(", ")) +
                    "; delays=" + delays +
                    ']';
        }
    }

    private ComputeLinkedVariables(StatementAnalysis statementAnalysis,
                                   Stage stage,
                                   BiPredicate<VariableInfoContainer, Variable> ignore,
                                   WeightedGraph weightedGraph,
                                   List<Cluster> clusters,
                                   Cluster returnValueCluster,
                                   Variable returnVariable,
                                   boolean allowBreakDelay,
                                   Set<Variable> linkingNotYetSet) {
        this.clusters = clusters;
        this.returnValueCluster = returnValueCluster;
        this.returnVariable = returnVariable;
        this.stage = stage;
        this.statementAnalysis = statementAnalysis;
        this.weightedGraph = weightedGraph;
        this.allowBreakDelay = allowBreakDelay;
        this.linkingNotYetSet = linkingNotYetSet;
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
        Set<Variable> linkingNotYetSet = new HashSet<>();
        AtomicReference<CausesOfDelay> encounteredNotYetSet = new AtomicReference<>(CausesOfDelay.EMPTY);
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
                if (curated == LinkedVariables.NOT_YET_SET) {
                    linkingNotYetSet.add(variable);
                    encounteredNotYetSet.set(curated.causesOfDelay());
                }
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
                    LinkedVariables curated =   add(statementAnalysis, stage, staticallyAssigned, ignore, reassigned, externalLinkedVariables,
                            evaluationContext, weightedGraph, delaysInClustering, vi1, variable);
                    if (curated == LinkedVariables.NOT_YET_SET) {
                        linkingNotYetSet.add(variable);
                        encounteredNotYetSet.set(curated.causesOfDelay());
                    }
                }
            }
        }
        ClusterResult cr = computeClusters(weightedGraph, variables,
                staticallyAssigned ? LinkedVariables.STATICALLY_ASSIGNED_DV : DV.MIN_INT_DV,
                staticallyAssigned ? LinkedVariables.STATICALLY_ASSIGNED_DV : LinkedVariables.DEPENDENT_DV,
                !staticallyAssigned,
                encounteredNotYetSet.get());

        // this will cause delays across the board for CONTEXT_ and EXTERNAL_ if the flag is set.
        if (evaluationContext.delayStatementBecauseOfECI()) {
            delaysInClustering.add(new SimpleCause(evaluationContext.getLocation(stage), CauseOfDelay.Cause.ECI_HELPER));
        }
        return new ComputeLinkedVariables(statementAnalysis, stage, ignore, weightedGraph, cr.clusters,
                cr.returnValueCluster, cr.rv, evaluationContext.allowBreakDelay(), linkingNotYetSet);
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

        boolean isBeingReassigned = reassigned.contains(variable);

        LinkedVariables external = externalLinkedVariables.apply(variable);
        LinkedVariables inVi = isBeingReassigned ? LinkedVariables.EMPTY
                : vi1.getLinkedVariables().remove(reassigned);
        LinkedVariables combined = external.merge(inVi);
        LinkedVariables refToScope = variable instanceof FieldReference fr ? combined.merge(linkToScope(fr)) : combined;
        LinkedVariables curatedBeforeIgnore;

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
        curatedBeforeIgnore = refToScope.removeIncompatibleWithImmutable(sourceImmutable, computeMyself, computeImmutable,
                immutableCanBeIncreasedByTypeParameters, computeImmutableHiddenContent);

        LinkedVariables curated = curatedBeforeIgnore
                .remove(v -> ignore.test(statementAnalysis.getVariableOrDefaultNull(v.fullyQualifiedName()), v));
        boolean bidirectional = !(variable instanceof ReturnVariable);
        weightedGraph.addNode(variable, curated.variables(), bidirectional, DV::min);
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
        return LinkedVariables.of(map);
    }

    private record ClusterResult(Cluster returnValueCluster, Variable rv, List<Cluster> clusters) {
    }

    private static ClusterResult computeClusters(WeightedGraph weightedGraph,
                                                 Set<Variable> variables,
                                                 DV minInclusive,
                                                 DV maxInclusive,
                                                 boolean followDelayed,
                                                 CausesOfDelay encounteredNotYetSet) {
        Set<Variable> done = new HashSet<>();
        List<Cluster> result = new ArrayList<>(variables.size());
        Cluster rvCluster = null;
        Variable rv = null;

        for (Variable variable : variables) {
            if (!done.contains(variable)) {
                Map<Variable, DV> map = weightedGraph.links(variable, maxInclusive, followDelayed);
                Set<Variable> reachable = map.entrySet().stream()
                        .filter(e -> e.getValue().ge(minInclusive) && e.getValue().le(maxInclusive))
                        .map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
                DV delays = map.values().stream().reduce(DV.MIN_INT_DV, DV::max);
                CausesOfDelay clusterDelay;
                if (encounteredNotYetSet.isDelayed() && followDelayed) {
                    clusterDelay = encounteredNotYetSet;
                } else {
                    clusterDelay = delays == DV.MIN_INT_DV ? CausesOfDelay.EMPTY : delays.causesOfDelay();
                }
                Cluster cluster = new Cluster(reachable, clusterDelay);
                if (variable instanceof ReturnVariable) {
                    rvCluster = cluster;
                    assert rv == null;
                    rv = variable;
                    done.add(rv);
                } else {
                    result.add(cluster);
                    assert Collections.disjoint(reachable, done) : "This is not good";
                    done.addAll(reachable);
                }
            }
        }
        return new ClusterResult(rvCluster, rv, result);
    }

    /**
     * break generated by ComputingParameterAnalyser, see FieldReference_3 as a primary example.
     * Cycle of 3 between a constructor parameter assigned to a field, with an accessor, and an instance method that calls the constructor.
     * <p>
     * allowBreakDelay is controlled by the primary type analyser; it gets activated when there was no progress anymore.
     */
    private DV propertyValuePotentiallyBreakDelay(Property property, Variable v, DV propertyValue) {
        if (allowBreakDelay && property == Property.CONTEXT_MODIFIED && propertyValue.isDelayed() &&
                (propertyValue.containsCauseOfDelay(CauseOfDelay.Cause.BREAK_MOM_DELAY,
                        c -> c instanceof SimpleCause sc && sc.location().getInfo() instanceof ParameterInfo) ||
                        // this second situation arises in InstanceOf_16: direct self reference
                        propertyValue.containsCauseOfDelay(CauseOfDelay.Cause.CONTEXT_MODIFIED,
                                c -> c instanceof SimpleCause sc && sc.location().getInfo() == v))) {
            LOGGER.debug("Breaking a MOM / CM delay for parameter  in {}", propertyValue);
            statementAnalysis.setBrokeDelay();
            return DV.FALSE_DV;
        }
        // normal action
        return propertyValue;
    }

    public ProgressAndDelay write(Property property, Map<Variable, DV> propertyValues) {
        CausesOfDelay causes = CausesOfDelay.EMPTY;
        boolean progress = false;
        boolean broken = false;

        // IMPORTANT: reduced code for the return variable, still a lot has been copied from the code further in this method
        if (returnVariable != null) {
            assert returnValueCluster.variables.stream().allMatch(propertyValues::containsKey);
            DV rvSummary = property.propertyType == Property.PropertyType.CONTEXT
                    ? property.falseDv
                    : returnValueCluster.variables.stream()
                    .map(v -> propertyValuePotentiallyBreakDelay(property, v, propertyValues.get(v)))
                    // IMPORTANT NOTE: falseValue gives 1 for IMMUTABLE and others, and sometimes we want the basis to be NOT_INVOLVED (0)
                    .reduce(DV.FALSE_DV, DV::max);
            if (rvSummary.isDelayed()) {
                causes = causes.merge(rvSummary.causesOfDelay());
            }
            VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(returnVariable.fullyQualifiedName());
            if (vic != null) {
                VariableInfo vi = vic.ensureLevelForPropertiesLinkedVariables(statementAnalysis.location(stage), stage);
                DV current = vi.getProperty(property);
                if (current.isDelayed()) {
                    try {
                        DV inMap = propertyValues.get(returnVariable);
                        if (property.bestDv.equals(inMap)) {
                            // whatever happens, this value cannot get better (e.g., TRUE in CM)
                            vic.setProperty(property, inMap, stage);
                            progress = true;
                        } else {
                            vic.setProperty(property, rvSummary, stage);
                            progress = rvSummary.isDone();
                        }
                    } catch (IllegalStateException ise) {
                        LOGGER.error("Return value cluster: {}", returnValueCluster);
                        throw ise;
                    }
                } else if (rvSummary.isDone() && !rvSummary.equals(current)) {
                    LOGGER.error("Variable {} in cluster {}", returnVariable, returnValueCluster.variables);
                    LOGGER.error("Property {}, current {}, new {}", property, current, rvSummary);
                    throw new UnsupportedOperationException("Overwriting value");
                }
            }
        }

        for (Cluster cluster : clusters) {
            assert cluster.variables.stream().allMatch(propertyValues::containsKey);
            DV summary = cluster.variables.stream()
                    .map(v -> propertyValuePotentiallyBreakDelay(property, v, propertyValues.get(v)))
                    // IMPORTANT NOTE: falseValue gives 1 for IMMUTABLE and others, and sometimes we want the basis to be NOT_INVOLVED (0)
                    .reduce(DV.FALSE_DV, DV::max);


            // See Modification_19 and _20, one which must have the delays (19) and the other which must have the break (20)
            // does not interfere with the next situation, as than one requires a done cluster
            if (Property.CONTEXT_MODIFIED == property && cluster.delays.isDelayed()) {
                if (allowBreakDelay && summary.valueIsFalse()) {
                    LOGGER.debug("Breaking linking delay on CM==FALSE, cluster {}", cluster);
                    statementAnalysis.setBrokeDelay();
                    broken = true;
                } else {
                    summary = summary.causesOfDelay().merge(cluster.delays);
                }
            }

            /*
            1st of 2 pieces of code that ensure that once a context property has reached its highest value in the
            previous statement, it should have it in this statement as well.
            First: if the cluster is done, and ANY of the previous ones reaches the highest value, then all should have it.
             */
            boolean clusterComplain;
            if (property.propertyType == Property.PropertyType.CONTEXT
                    && !summary.equals(property.bestDv)
                    && cluster.delays.isDone()
                    && cluster.variables.size() > 1) {
                // if any of the previous values has a max value, we'll need to have it, too
                DV best = cluster.variables.stream().map(v -> {
                    VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(v.fullyQualifiedName());
                    if (vic != null) {
                        VariableInfo vi1 = vic.getPreviousOrInitial();
                        return vi1.getProperty(property);
                    } else {
                        return property.falseDv;
                    }
                }).reduce(DV.MIN_INT_DV, DV::max);
                if (best.equals(property.bestDv)) {
                    summary = best;
                    clusterComplain = false;
                } else {
                    clusterComplain = true;
                }
            } else {
                clusterComplain = true;
            }
            if (summary.isDelayed()) {
                causes = causes.merge(summary.causesOfDelay());
            }
            DV newValue1 = summary;
            for (Variable variable : cluster.variables) {
                VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(variable.fullyQualifiedName());
                if (vic != null) {
                    DV newValue;
                    DV override = vic.propertyOverrides().getOrDefaultNull(property);
                    boolean complain;
                    if (override != null) {
                        newValue = override;
                        complain = false;
                    } else {
                        newValue = newValue1;
                        complain = clusterComplain;
                    }
                    VariableInfo vi = vic.ensureLevelForPropertiesLinkedVariables(statementAnalysis.location(stage), stage);
                    DV current = vi.getProperty(property);
                    VariableInfo vi1 = vic.getPreviousOrInitial();
                    DV previous = vi1.getProperty(property);
                    if (property.propertyType == Property.PropertyType.CONTEXT && property.bestDv.equals(previous)) {
                        /*
                        Second of code that ensures that once a context property has reached its highest value,
                        it should keep it in subsequent statements. Note that this
                        does not work for external properties, as they are reset to NOT_INVOLVED after an assignment
                         */
                        if (current.isDelayed()) {
                            vic.setProperty(property, property.bestDv, stage);
                            progress = true;
                        } // else don't complain!!!
                    } else if (current.isDelayed()) {
                        try {
                            DV inMap = propertyValues.get(variable);
                            if (property.bestDv.equals(inMap)) {
                                // whatever happens, this value cannot get better (e.g., TRUE in CM)
                                vic.setProperty(property, property.bestDv, stage);
                                progress = true;
                            } else {
                                vic.setProperty(property, newValue, stage);
                                progress |= newValue.isDone();
                                if (broken) {
                                    LOGGER.debug("**** Setting CM of {} to false in stmt {}", variable,
                                            statementAnalysis.index());
                                }
                            }
                        } catch (IllegalStateException ise) {
                            LOGGER.error("Current cluster: {}", cluster);
                            throw ise;
                        }

                    } else if (complain && newValue.isDone() && !newValue.equals(current)) {
                        LOGGER.error("Variable {} in cluster {}", variable, cluster.variables);
                        LOGGER.error("Property {}, current {}, new {}", property, current, newValue);
                        throw new UnsupportedOperationException("Overwriting value");
                    }
                }
            }
        }
        return new ProgressAndDelay(progress, causes);
    }

    private Map<Variable, Set<Variable>> staticallyAssignedVariables() {
        // computed on the 0 values
        Map<Variable, Set<Variable>> staticallyAssigned = new HashMap<>();
        for (Cluster cluster : clusters) {
            for (Variable variable : cluster.variables) {
                Set<Variable> set = new HashSet<>(cluster.variables);
                set.remove(variable); // remove self-reference
                staticallyAssigned.put(variable, set);
            }
        }
        if (returnVariable != null) {
            Set<Variable> set = new HashSet<>(returnValueCluster.variables);
            set.remove(returnVariable); // remove self-reference
            staticallyAssigned.put(returnVariable, set);
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
    public ProgressAndDelay writeClusteredLinkedVariables(ComputeLinkedVariables staticallyAssignedCLV) {
        Map<Variable, Set<Variable>> staticallyAssigned = staticallyAssignedCLV.staticallyAssignedVariables();
        CausesOfDelay causes = CausesOfDelay.EMPTY;
        boolean progress = false;
        for (Cluster cluster : clusters) {
            for (Variable variable : cluster.variables) {
                VariableInfoContainer vic = statementAnalysis.getVariable(variable.fullyQualifiedName());

                Map<Variable, DV> map = weightedGraph.links(variable, null, true);
                LinkedVariables linkedVariables = applyStaticallyAssignedAndRemoveSelfReference(staticallyAssigned,
                        variable, map, cluster.delays);

                causes = causes.merge(linkedVariables.causesOfDelay());
                vic.ensureLevelForPropertiesLinkedVariables(statementAnalysis.location(stage), stage);
                progress |= writeLinkedVariables(cluster, variable, vic, linkedVariables);
                staticallyAssigned.remove(variable);
            }
        }

        if (returnVariable != null) {
            // TODO completely copied the code
            VariableInfoContainer vicRv = statementAnalysis.getVariable(returnVariable.fullyQualifiedName());

            Map<Variable, DV> map = weightedGraph.links(returnVariable, null, true);
            LinkedVariables linkedVariables = applyStaticallyAssignedAndRemoveSelfReference(staticallyAssigned,
                    returnVariable, map, returnValueCluster.delays);

            causes = causes.merge(linkedVariables.causesOfDelay());
            vicRv.ensureLevelForPropertiesLinkedVariables(statementAnalysis.location(stage), stage);
            progress |= writeLinkedVariables(returnValueCluster, returnVariable, vicRv, linkedVariables);
            staticallyAssigned.remove(returnVariable);
        }

        // there may be variables remaining, which were present in linking that is not removed in the first phase
        // Occurs in statement 3, Basics_24, to the "map" variable
        for (Variable variable : staticallyAssigned.keySet()) {
            VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(variable.fullyQualifiedName());
            assert vic != null : "No variable named " + variable.fullyQualifiedName();
            vic.ensureLevelForPropertiesLinkedVariables(statementAnalysis.location(stage), stage);
            progress |= writeLinkedVariables(null, variable, vic, LinkedVariables.EMPTY);
        }
        return new ProgressAndDelay(progress, causes);
    }

    private boolean writeLinkedVariables(Cluster cluster, Variable variable, VariableInfoContainer vic, LinkedVariables linkedVariables) {
        try {
            return vic.setLinkedVariables(linkedVariables, stage);
        } catch (IllegalStateException isa) {
            LOGGER.error("Linked variables change in illegal way in stmt {}: {}", statementAnalysis.index(), isa);
            LOGGER.error("Variable: {}", variable);
            LOGGER.error("Cluster : {}", cluster);
            throw isa;
        }
    }

    private LinkedVariables applyStaticallyAssignedAndRemoveSelfReference(Map<Variable, Set<Variable>> staticallyAssignedVariables,
                                                                          Variable variable,
                                                                          Map<Variable, DV> map,
                                                                          CausesOfDelay clusterDelays) {
        if (clusterDelays.isDelayed()) {
            for (Map.Entry<Variable, DV> entry : map.entrySet()) {
                entry.setValue(clusterDelays);
            }
        }
        Set<Variable> staticallyAssigned = staticallyAssignedVariables.get(variable);
        if (staticallyAssigned != null) {
            staticallyAssigned.forEach(v -> map.put(v, LinkedVariables.STATICALLY_ASSIGNED_DV));
        }
        map.remove(variable); // no self references
        if (map.isEmpty()) {
            if (clusterDelays.isDelayed()) return LinkedVariables.NOT_YET_SET;
            return LinkedVariables.EMPTY;
        }
        return LinkedVariables.of(map);
    }

    /**
     * This variant is currently used by copyBackLocalCopies in StatementAnalysisImpl.
     * It touches all variables rather than those in clusters only.
     */
    public boolean writeLinkedVariables(ComputeLinkedVariables staticallyAssignedCLV,
                                        Set<Variable> touched,
                                        Set<Variable> toRemove) {
        assert stage == Stage.MERGE;
        Map<Variable, Set<Variable>> staticallyAssignedVariables = staticallyAssignedCLV.staticallyAssignedVariables();
        AtomicBoolean progress = new AtomicBoolean();
        statementAnalysis.rawVariableStream()
                .forEach(e -> {
                    VariableInfoContainer vic = e.getValue();

                    Variable variable = vic.current().variable();
                    if (touched.contains(variable)) {
                        Map<Variable, DV> map = weightedGraph.links(variable, null, true);
                        map.keySet().removeIf(toRemove::contains);

                        Cluster cluster = clusters.stream().filter(c -> c.variables.contains(variable)).findFirst().orElse(null);
                        CausesOfDelay clusterDelay = linkingNotYetSet.contains(variable) ? LinkedVariables.NOT_YET_SET_DELAY
                                : cluster != null && cluster.delays.isDelayed() ? cluster.delays
                                : CausesOfDelay.EMPTY;

                        LinkedVariables linkedVariables = applyStaticallyAssignedAndRemoveSelfReference(staticallyAssignedVariables,
                                variable, map, clusterDelay);
                        vic.ensureLevelForPropertiesLinkedVariables(statementAnalysis.location(Stage.MERGE), Stage.MERGE);
                        if (vic.setLinkedVariables(linkedVariables, stage)) {
                            progress.set(true);
                        }
                    }
                });
        return progress.get();
    }

    /*
    clusters which contain parameters (or fields), get the CNN_TRAVELS_TO_PC flag set

    we have to try multiple times, because the nature of variableLinkedToLoopVariable(),
    which may connect one cluster to another, potentially not yet processed.
     */
    public boolean writeCnnTravelsToFields(AnalyserContext analyserContext, boolean cnnTravelsToFields) {
        boolean change = true;
        boolean progress = false;
        while (change) {
            change = false;
            for (Cluster cluster : clusters) {
                boolean activate = cluster.variables.stream().anyMatch(v -> {
                    Either<CausesOfDelay, Set<Variable>> either = statementAnalysis.recursivelyLinkedToParameterOrField(
                            analyserContext, v, cnnTravelsToFields);
                    return either.isRight() && !either.getRight().isEmpty();
                });
                if (activate) {
                    for (Variable variable : cluster.variables) {
                        VariableInfoContainer vic = statementAnalysis.getVariable(variable.fullyQualifiedName());
                        DV current = vic.best(Stage.EVALUATION).getProperty(Property.CNN_TRAVELS_TO_PRECONDITION, null);
                        if (current == null) {
                            vic.setProperty(Property.CNN_TRAVELS_TO_PRECONDITION, DV.TRUE_DV, Stage.EVALUATION);
                            change = true;
                            progress = true;
                        }
                    }
                }
            }
            // note: ignores the returnValueCluster
        }
        return progress;
    }
}
