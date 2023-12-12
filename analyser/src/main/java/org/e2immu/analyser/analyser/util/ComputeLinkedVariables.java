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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.ProgressAndDelay;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analyser.impl.util.BreakDelayLevel;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.variable.*;
import org.e2immu.support.Either;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.LinkedVariables.*;
import static org.e2immu.analyser.analyser.Stage.EVALUATION;

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
    private final List<WeightedGraph.Cluster> clusters;
    private final Set<Variable> variablesInClusters;
    private final WeightedGraph.Cluster returnValueCluster;
    private final Variable returnVariable;
    private final ShortestPath shortestPath;
    private final BreakDelayLevel breakDelayLevel;
    private final Set<Variable> linkingNotYetSet;
    private final boolean oneBranchHasBecomeUnreachable;

    private ComputeLinkedVariables(StatementAnalysis statementAnalysis,
                                   Stage stage,
                                   BiPredicate<VariableInfoContainer, Variable> ignore,
                                   ShortestPath shortestPath,
                                   Set<Variable> variablesInClusters,
                                   List<WeightedGraph.Cluster> clusters,
                                   WeightedGraph.Cluster returnValueCluster,
                                   Variable returnVariable,
                                   BreakDelayLevel breakDelayLevel,
                                   boolean oneBranchHasBecomeUnreachable,
                                   Set<Variable> linkingNotYetSet) {
        this.clusters = clusters;
        this.returnValueCluster = returnValueCluster;
        this.returnVariable = returnVariable;
        this.stage = stage;
        this.statementAnalysis = statementAnalysis;
        this.shortestPath = shortestPath;
        this.breakDelayLevel = breakDelayLevel;
        this.linkingNotYetSet = linkingNotYetSet;
        this.oneBranchHasBecomeUnreachable = oneBranchHasBecomeUnreachable;
        this.variablesInClusters = variablesInClusters;
    }

    public static ComputeLinkedVariables create(StatementAnalysis statementAnalysis,
                                                Stage stage,
                                                boolean oneBranchHasBecomeUnreachable,
                                                BiPredicate<VariableInfoContainer, Variable> ignore,
                                                Set<Variable> reassigned,
                                                Function<Variable, LinkedVariables> externalLinkedVariables,
                                                EvaluationContext evaluationContext) {
        WeightedGraph weightedGraph = new WeightedGraphImpl(evaluationContext.getAnalyserContext().getCache());
        // we keep track of all variables at the level, PLUS variables linked to, which are not at the level
        Set<Variable> done = new HashSet<>();
        Set<Variable> linkingNotYetSet = new HashSet<>();

        Set<VariableInfoContainer> start = statementAnalysis.variableEntryStream(stage)
                .map(Map.Entry::getValue).collect(Collectors.toUnmodifiableSet());
        boolean iteration1Plus = false;
        while (!start.isEmpty()) {
            Set<VariableInfoContainer> linked = new HashSet<>();
            for (VariableInfoContainer vic : start) {
                Variable variable = vic.current().variable();
                if (iteration1Plus || !ignore.test(vic, variable)) {
                    done.add(variable);
                    VariableInfo vi1 = vic.getPreviousOrInitial();
                    VariableInfo viE = vic.best(EVALUATION);
                    LinkedVariables linkedVariables = add(statementAnalysis, ignore, reassigned, externalLinkedVariables,
                            weightedGraph, vi1, viE, variable);
                    for (Map.Entry<Variable, DV> e : linkedVariables) {
                        Variable v = e.getKey();
                        if (!done.contains(v)) {
                            VariableInfoContainer linkedVic = statementAnalysis.getVariableOrDefaultNull(v.fullyQualifiedName());
                            if (linkedVic != null) {
                                linked.add(linkedVic);
                            }
                        }
                    }
                    if (linkedVariables == LinkedVariables.NOT_YET_SET) {
                        linkingNotYetSet.add(variable);
                    }
                }
            }
            linked.removeIf(vic -> done.contains(vic.current().variable()));
            start = new HashSet<>(linked);
            iteration1Plus = true;
        }
        augmentGraph(weightedGraph);
        WeightedGraph.ClusterResult cr = weightedGraph.staticClusters();
        ShortestPath shortestPath = weightedGraph.shortestPath();

        return new ComputeLinkedVariables(statementAnalysis, stage, ignore, shortestPath,
                cr.variablesInClusters(), cr.clusters(), cr.returnValueCluster(),
                cr.rv(), evaluationContext.breakDelayLevel(), oneBranchHasBecomeUnreachable,
                linkingNotYetSet);
    }

    /*
    when a variable points LINK_IS_HC_OF to 2 other variables, these two variables must be linked with COMMON_HC,
    unless there's already a lower link between them, en which case, we take the lower one.

    See e.g. ListUtil for a nice example, which is pretty common.
     */
    private static void augmentGraph(WeightedGraph weightedGraph) {
        Map<Variable, Map<Variable, DV>> toAdd3 = new HashMap<>();
        ShortestPath shortestPath = weightedGraph.shortestPath();
        weightedGraph.visit((v, map) -> {
            if (map != null) {
                CausesOfDelay mapped3Delays = CausesOfDelay.EMPTY;
                List<Variable> mapped3 = new ArrayList<>();
                for (Map.Entry<Variable, DV> entry : map.entrySet()) {
                    Variable vv = entry.getKey();
                    if (!(vv instanceof This)
                            && !vv.parameterizedType().isPrimitiveExcludingVoid()
                            && (entry.getValue().equals(LinkedVariables.LINK_IS_HC_OF) || entry.getValue().isDelayed())) {
                        mapped3.add(vv);
                        mapped3Delays = mapped3Delays.merge(entry.getValue().causesOfDelay());
                    }
                }
                if (mapped3.size() > 1) {
                    LOGGER.trace("Augmenting links: found {}", mapped3);
                    for (int i = 0; i < mapped3.size(); i++) {
                        Variable v1 = mapped3.get(i);
                        for (Variable v2 : mapped3.subList(i + 1, mapped3.size())) {
                            DV minValuePresent;
                            DV v1ToV2 = weightedGraph.edgeValueOrNull(v1, v2);
                            DV v2ToV1 = weightedGraph.edgeValueOrNull(v2, v1);
                            if (mapped3Delays.isDelayed()) {
                                minValuePresent = mapped3Delays;
                            } else {
                                DV v1ToV2Value = Objects.requireNonNullElse(v1ToV2, LINK_COMMON_HC);
                                DV v2ToV1Value = Objects.requireNonNullElse(v2ToV1, LINK_COMMON_HC);
                                minValuePresent = v1ToV2Value.min(v2ToV1Value); // could still be a delay!!!
                            }
                            if (v1ToV2 == null) {
                                Map<Variable, DV> to = toAdd3.computeIfAbsent(v1, k -> new HashMap<>());
                                if (!to.containsKey(v2)) {
                                    DV shortest = shortestPath.links(v1, null).get(v2);
                                    DV min = shortest == null ? minValuePresent : minValuePresent.min(shortest);
                                    to.put(v2, min);
                                }
                            }
                            if (v2ToV1 == null) {
                                Map<Variable, DV> to = toAdd3.computeIfAbsent(v2, k -> new HashMap<>());
                                if (!to.containsKey(v1)) {
                                    DV shortest = shortestPath.links(v2, null).get(v1);
                                    DV min = shortest == null ? minValuePresent : minValuePresent.min(shortest);
                                    to.put(v1, min);
                                }
                            }
                        }
                    }
                }
            }
        });
        for (Map.Entry<Variable, Map<Variable, DV>> entry : toAdd3.entrySet()) {
            LOGGER.trace("Augmenting links: from {} to {}", entry.getKey().simpleName(), entry.getValue());
            weightedGraph.addNode(entry.getKey(), entry.getValue(), false, (v1, v2) -> {
                throw new UnsupportedOperationException();
            });
        }
    }

    private static LinkedVariables add(StatementAnalysis statementAnalysis,
                                       BiPredicate<VariableInfoContainer, Variable> ignore,
                                       Set<Variable> reassigned,
                                       Function<Variable, LinkedVariables> externalLinkedVariables,
                                       WeightedGraph weightedGraph,
                                       VariableInfo vi1,
                                       VariableInfo viE,
                                       Variable variable) {
        boolean isBeingReassigned = reassigned.contains(variable);

        LinkedVariables external = Objects.requireNonNullElse(externalLinkedVariables.apply(variable), LinkedVariables.EMPTY);
        LinkedVariables inVi = isBeingReassigned ? LinkedVariables.EMPTY
                : vi1.getLinkedVariables().remove(reassigned);
        LinkedVariables combined = external.merge(inVi);
        LinkedVariables refToScope = variable instanceof FieldReference fr ? combined.merge(linkToScope(fr)) : combined;

        LinkedVariables curated = refToScope
                .remove(v -> ignore.test(statementAnalysis.getVariableOrDefaultNull(v.fullyQualifiedName()), v));
        if (variable instanceof This) {
            curated = LinkedVariables.EMPTY;
        } else if (viE != vi1
                && viE.getValue() instanceof DelayedVariableExpression dve
                && dve.msg.startsWith("<vl:")
                && !curated.isDelayed()) {
            curated = curated.changeNonStaticallyAssignedToDelay(viE.getValue().causesOfDelay());
        }
        weightedGraph.addNode(variable, curated.variables(), false, DV::min);
        return curated;
    }

    private static LinkedVariables linkToScope(FieldReference fr) {
        Set<Variable> variables = fr.scope().variablesWithoutCondition().stream()
                .filter(v -> !(v instanceof This))
                .collect(Collectors.toUnmodifiableSet());
        DV link = fr.scope().isDelayed() ? fr.scope().causesOfDelay() : LinkedVariables.LINK_DEPENDENT;
        Map<Variable, DV> map = variables.stream().collect(Collectors.toUnmodifiableMap(v -> v, v -> link));
        return LinkedVariables.of(map);
    }

    public ProgressAndDelay write(Property property, Map<Variable, DV> propertyValues) {
        return write(property, propertyValues, CausesOfDelay.EMPTY);
    }

    public ProgressAndDelay write(Property property, Map<Variable, DV> propertyValues, CausesOfDelay extraDelay) {
        try {
            return writeProperty(property, propertyValues, extraDelay);
        } catch (IllegalStateException ise) {
            LOGGER.error("Clusters assigned are: {}", clusters);
            throw ise;
        }
    }

    private ProgressAndDelay writeProperty(Property property, Map<Variable, DV> propertyValues, CausesOfDelay extraDelay) {
        CausesOfDelay causes = CausesOfDelay.EMPTY;
        boolean progress;

        // IMPORTANT: reduced code for the return variable, still a lot has been copied from the code further in this method
        if (returnVariable != null) {
            assert returnValueCluster.variables().stream().allMatch(propertyValues::containsKey);
            DV rvSummary = property.propertyType == Property.PropertyType.CONTEXT
                    ? property.falseDv
                    : returnValueCluster.variables().stream().map(propertyValues::get)
                    // IMPORTANT NOTE: falseValue gives 1 for IMMUTABLE and others, and sometimes we want the basis to be NOT_INVOLVED (0)
                    .reduce(property.valueWhenAbsent(), DV::max);
            if (rvSummary.isDelayed()) {
                causes = causes.merge(rvSummary.causesOfDelay());
            }
            progress = writeReturnValueProperty(property, propertyValues, rvSummary);
        } else {
            progress = false;
        }

        boolean broken = false;
        for (WeightedGraph.Cluster cluster : clusters) {
            assert cluster.variables().stream().allMatch(propertyValues::containsKey);
            DV summary = cluster.variables().stream()
                    .map(propertyValues::get)
                    // IMPORTANT NOTE: falseValue gives 1 for IMMUTABLE and others, and sometimes we want the basis to be NOT_INVOLVED (0)
                    .reduce(property.valueWhenAbsent(), DV::max);

            // extraDelay: when merging, but the conditions of the different merge constituents are not yet done
            // currently only for CM; example: TrieSimplified_0, _1_2, _1_2bis
            if (extraDelay.isDelayed()) {
                CausesOfDelay conditionDelayMarker = DelayFactory.createDelay(new SimpleCause(statementAnalysis.location(stage), CauseOfDelay.Cause.CONDITION));
                summary = extraDelay.merge(conditionDelayMarker);
            }
            if (!Collections.disjoint(linkingNotYetSet, cluster.variables()) && summary.isDone()) {
                summary = LinkedVariables.NOT_YET_SET_DELAY;
            }
            /*
            1st of 2 pieces of code that ensure that once a context property has reached its highest value in the
            previous statement, it should have it in this statement as well.
            First: if the cluster is done, and ANY of the previous ones reaches the highest value, then all should have it.
             */
            boolean clusterComplain;
            if (property.propertyType == Property.PropertyType.CONTEXT
                    && !summary.equals(property.bestDv)
                    && cluster.variables().size() > 1) {
                // if any of the previous values has a max value, we'll need to have it, too
                DV best = cluster.variables().stream().map(v -> {
                    VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(v.fullyQualifiedName());
                    if (vic != null) {
                        VariableInfo vi1 = vic.getPreviousOrInitial();
                        return vi1.getProperty(property);
                    } else {
                        return property.falseDv;
                    }
                }).reduce(DelayFactory.initialDelay(), DV::max);
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
            for (Variable variable : cluster.variables()) {
                progress |= setPropertyOneVariable(variable, property, newValue1, propertyValues, extraDelay.isDone(),
                        clusterComplain, broken, cluster);
            }
        }
        return new ProgressAndDelay(progress, causes);
    }

    private boolean writeReturnValueProperty(Property property,
                                             Map<Variable, DV> propertyValues,
                                             DV rvSummary) {
        VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(returnVariable.fullyQualifiedName());
        boolean progress;
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
                LOGGER.error("Variable {} in cluster {}", returnVariable, returnValueCluster.variables());
                LOGGER.error("Property {}, current {}, new {}", property, current, rvSummary);
                throw new UnsupportedOperationException("Overwriting value");
            } else progress = false;
        } else progress = false;
        return progress;
    }

    private boolean setPropertyOneVariable(Variable variable,
                                           Property property,
                                           DV valueInput,
                                           Map<Variable, DV> propertyValues,
                                           boolean extraDelayIsDone,
                                           boolean clusterComplain,
                                           boolean brokenForDebugging,
                                           WeightedGraph.Cluster clusterForDebugging) {
        VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(variable.fullyQualifiedName());
        boolean progress = false;
        if (vic != null) {
            VariableInfo vi = vic.ensureLevelForPropertiesLinkedVariables(statementAnalysis.location(stage), stage);
            DV current = vi.getProperty(property);

            DV value;
            boolean complain;
            if ((property == Property.CONTEXT_NOT_NULL || property == Property.CONTEXT_IMMUTABLE
                    || property == Property.CONTEXT_CONTAINER) && oneBranchHasBecomeUnreachable) {
                value = valueInput;
                complain = false;
            } else if (valueInput.isDone()
                    && property.propertyType == Property.PropertyType.EXTERNAL
                    && MultiLevel.NOT_INVOLVED_DV.equals(current)
                    && !valueInput.equals(current)) {
                // See Lambda_19Recursion... don't see another way out; in the lambda we don't see the assignment
                value = MultiLevel.NOT_INVOLVED_DV;
                complain = clusterComplain;
            } else {
                value = valueInput;
                complain = clusterComplain;
            }
            VariableInfo vi1 = vic.getPreviousOrInitial();
            DV previous = vi1.getProperty(property);
            boolean contextProperty = property.propertyType == Property.PropertyType.CONTEXT;
            if (contextProperty && property.bestDv.equals(previous)) {
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
                    if (contextProperty && property.bestDv.equals(inMap) && extraDelayIsDone) {
                        // whatever happens, this value cannot get better (e.g., TRUE in CM)
                        vic.setProperty(property, property.bestDv, stage);
                        progress = true;
                    } else {
                        vic.setProperty(property, value, stage);
                        progress = value.isDone();
                        if (brokenForDebugging) {
                            LOGGER.debug("**** Setting CM of {} to false in stmt {}", variable, statementAnalysis.index());
                        }
                    }
                } catch (IllegalStateException ise) {
                    LOGGER.error("Current cluster: {}", clusterForDebugging);
                    throw ise;
                }

            } else if (value.isDone() && !value.equals(current)) {
                if (complain) {
                    LOGGER.error("Variable {} in cluster {}", variable.simpleName(), clusterForDebugging);
                    LOGGER.error("Property {}, current {}, new {}", property, current, value);
                    throw new UnsupportedOperationException("Overwriting value");
                }
                if (oneBranchHasBecomeUnreachable) {
                    LOGGER.debug("Not overwriting property {} of variable {}, (at least) one branch has become unreachable",
                            property, variable);
                }
            }
        }
        return progress;
    }

    private Map<Variable, Set<Variable>> staticallyAssignedVariables() {
        // computed on the 0 values
        Map<Variable, Set<Variable>> staticallyAssigned = new HashMap<>();
        for (WeightedGraph.Cluster cluster : clusters) {
            for (Variable variable : cluster.variables()) {
                Set<Variable> set = new HashSet<>(cluster.variables());
                set.remove(variable); // remove self-reference
                staticallyAssigned.put(variable, set);
            }
        }
        if (returnVariable != null) {
            Set<Variable> set = new HashSet<>(returnValueCluster.variables());
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
    public ProgressAndDelay writeClusteredLinkedVariables() {
        Map<Variable, Set<Variable>> staticallyAssigned = staticallyAssignedVariables();
        CausesOfDelay causes = CausesOfDelay.EMPTY;
        boolean progress = false;

        for (Variable variable : variablesInClusters) {
            VariableInfoContainer vic = statementAnalysis.getVariable(variable.fullyQualifiedName());

            Map<Variable, DV> map = shortestPath.links(variable, null);
            LinkedVariables linkedVariables = applyStaticallyAssignedAndRemoveSelfReference(staticallyAssigned,
                    variable, map);

            causes = causes.merge(linkedVariables.causesOfDelay());
            vic.ensureLevelForPropertiesLinkedVariables(statementAnalysis.location(stage), stage);
            progress |= writeLinkedVariables(variable, vic, linkedVariables);
            staticallyAssigned.remove(variable);
        }

        if (returnVariable != null) {
            VariableInfoContainer vicRv = statementAnalysis.getVariable(returnVariable.fullyQualifiedName());
            Map<Variable, DV> map = shortestPath.links(returnVariable, null);
            LinkedVariables linkedVariables = applyStaticallyAssignedAndRemoveSelfReference(staticallyAssigned,
                    returnVariable, map);

            causes = causes.merge(linkedVariables.causesOfDelay());
            vicRv.ensureLevelForPropertiesLinkedVariables(statementAnalysis.location(stage), stage);
            progress |= writeLinkedVariables(returnVariable, vicRv, linkedVariables);
            staticallyAssigned.remove(returnVariable);
        }

        // there may be variables remaining, which were present in linking that is not removed in the first phase
        // Occurs in statement 3, Basics_24, to the "map" variable
        for (Variable variable : staticallyAssigned.keySet()) {
            VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(variable.fullyQualifiedName());
            assert vic != null : "No variable named " + variable.fullyQualifiedName();
            vic.ensureLevelForPropertiesLinkedVariables(statementAnalysis.location(stage), stage);
            progress |= writeLinkedVariables(variable, vic, LinkedVariables.EMPTY);
        }
        return new ProgressAndDelay(progress, causes);
    }

    private boolean writeLinkedVariables(Variable variable, VariableInfoContainer vic, LinkedVariables linkedVariables) {
        try {
            return vic.setLinkedVariables(linkedVariables, stage);
        } catch (IllegalStateException isa) {
            LOGGER.error("Linked variables change in illegal way in stmt {}", statementAnalysis.index());
            LOGGER.error("Variable: {}", variable);
            throw isa;
        }
    }

    private LinkedVariables applyStaticallyAssignedAndRemoveSelfReference(Map<Variable, Set<Variable>> staticallyAssignedVariables,
                                                                          Variable variable,
                                                                          Map<Variable, DV> map) {
        if (linkingNotYetSet.contains(variable)) {
            return NOT_YET_SET;
        }
        CausesOfDelay allDelays = map.values().stream()
                .map(DV::causesOfDelay)
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);

        if (allDelays.isDelayed()) {
            for (Map.Entry<Variable, DV> entry : map.entrySet()) {
                entry.setValue(allDelays);
            }
        }
        Set<Variable> staticallyAssigned = staticallyAssignedVariables.get(variable);
        if (staticallyAssigned != null) {
            staticallyAssigned.forEach(v -> map.put(v, LINK_STATICALLY_ASSIGNED));
        }
        map.remove(variable); // no self references
        if (map.isEmpty()) {
            if (allDelays.isDelayed()) return LinkedVariables.NOT_YET_SET;
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
                                        Set<Variable> toRemove,
                                        Set<Variable> haveLinkedVariables) {
        assert stage == Stage.MERGE;
        Map<Variable, Set<Variable>> staticallyAssignedVariables = staticallyAssignedCLV.staticallyAssignedVariables();
        AtomicBoolean progress = new AtomicBoolean();
        statementAnalysis.rawVariableStream()
                .forEach(e -> {
                    VariableInfoContainer vic = e.getValue();

                    Variable variable = vic.current().variable();
                    if (touched.contains(variable)) {
                        LinkedVariables linkedVariables;
                        if (haveLinkedVariables.contains(variable)) {
                            Map<Variable, DV> map = shortestPath.links(variable, null);
                            map.keySet().removeIf(toRemove::contains);
                            linkedVariables = applyStaticallyAssignedAndRemoveSelfReference(staticallyAssignedVariables,
                                    variable, map);
                        } else {
                            VariableInfo eval = vic.best(EVALUATION);
                            linkedVariables = eval.getLinkedVariables();
                        }

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
        boolean seenChange = false;
        while (change) {
            change = false;
            for (WeightedGraph.Cluster cluster : clusters) {
                boolean activate = cluster.variables().stream().anyMatch(v -> {
                    Either<CausesOfDelay, Set<Variable>> either = statementAnalysis.recursivelyLinkedToParameterOrField(
                            analyserContext, v, cnnTravelsToFields);
                    return either.isRight() && !either.getRight().isEmpty();
                });
                for (Variable variable : cluster.variables()) {
                    VariableInfoContainer vic = statementAnalysis.getVariable(variable.fullyQualifiedName());
                    if (vic.variableNature() != VariableNature.FROM_ENCLOSING_METHOD) {
                        DV current = vic.best(Stage.EVALUATION).getProperty(Property.CNN_TRAVELS_TO_PRECONDITION,
                                null);
                        if (current == null) {
                            vic.setProperty(Property.CNN_TRAVELS_TO_PRECONDITION, DV.fromBoolDv(activate),
                                    Stage.EVALUATION);
                            change = true;
                            seenChange = true;
                        }
                    } // else: set in StatementAnalysisImpl.initIteration1Plus
                }
            }
            // note: ignores the returnValueCluster
        }
        return seenChange;
    }


    /**
     * break generated by ComputingParameterAnalyser, see FieldReference_3 as a primary example.
     * Cycle of 3 between a constructor parameter assigned to a field, with an accessor, and an instance method that
     * calls the constructor.
     * <p>
     * allowBreakDelay is controlled by the primary type analyser; it gets activated when there was no progress anymore.
     */
    private DV potentiallyBreakContextModifiedDelay(Variable v, DV propertyValue) {
        if (breakDelayLevel.acceptStatement()
                && propertyValue.isDelayed()
                && (propertyValue.containsCauseOfDelay(CauseOfDelay.Cause.BREAK_MOM_DELAY,
                c -> c instanceof SimpleCause sc && sc.location().getInfo() instanceof ParameterInfo) ||
                // this second situation arises in InstanceOf_16: direct self reference
                propertyValue.containsCauseOfDelay(CauseOfDelay.Cause.CONTEXT_MODIFIED,
                        c -> c instanceof SimpleCause sc && sc.location().getInfo() == v))) {
            LOGGER.debug("Breaking a MOM / CM delay for parameter {} in {}", v, propertyValue);
            statementAnalysis.setBrokeDelay();
            return DV.FALSE_DV;
        }
        // normal action
        return propertyValue;
    }

    /*
    difference wrt the normal property writing: not using the clusters!!

    keep a map with final values, maxed values
    for each variable, compute the linked variables via the weighted graph
       each of these gets the value from the starting variable
    only when we looped over all, we can start writing out values

     */
    public ProgressAndDelay writeContextModified(AnalyserContext analyserContext,
                                                 Map<Variable, DV> propertyMap,
                                                 Map<Variable, Integer> modificationTimeIncrements,
                                                 int statementTimeDelta,
                                                 Map<Variable, Integer> currentModificationTimes,
                                                 CausesOfDelay extraDelayIn,
                                                 boolean noAssignments) {
        Map<Variable, DV> finalModified = computeFinalModified(propertyMap, extraDelayIn, noAssignments);
        /*
        As soon as there is one override, later linking may cause other variables to change value,
        see DGSimplified_3 as an example. So we can't ignore complaints on an individual basis.
         */
        boolean haveOverride = variablesInClusters.stream().anyMatch(v -> {
            VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(v.fullyQualifiedName());
            return vic != null && vic.propertyOverrides().getOrDefaultNull(Property.CONTEXT_MODIFIED) != null;
        });
        boolean progress = false;
        CausesOfDelay causesOfDelay = CausesOfDelay.EMPTY;
        for (Variable variable : variablesInClusters) {
            DV newValue = finalModified.get(variable);
            assert newValue != null;
            VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(variable.fullyQualifiedName());
            if (vic != null) {
                DV override = vic.propertyOverrides().getOrDefaultNull(Property.CONTEXT_MODIFIED);
                DV newValueAfterOverride = Objects.requireNonNullElse(override, newValue);

                VariableInfo vi = vic.ensureLevelForPropertiesLinkedVariables(statementAnalysis.location(stage), stage);
                DV current = vi.getProperty(Property.CONTEXT_MODIFIED);
                if (current.isDelayed()) {
                    try {
                        vic.setProperty(Property.CONTEXT_MODIFIED, newValueAfterOverride, stage);
                        progress |= newValueAfterOverride.isDone();
                        causesOfDelay = causesOfDelay.merge(newValue.causesOfDelay());
                    } catch (IllegalStateException ise) {
                        LOGGER.error("Return value cluster: {}", returnValueCluster);
                        throw ise;
                    }
                } else if (newValueAfterOverride.isDone() && !newValueAfterOverride.equals(current)) {
                    if (haveOverride) {
                        LOGGER.debug("Ignoring difference, keep context modified {} on {}", current, variable);
                    } else {
                        LOGGER.error("Variable {}, property CONTEXT_MODIFIED, current {}, new {}", variable, current,
                                newValueAfterOverride);
                        throw new UnsupportedOperationException("Overwriting value");
                    }
                }
            }
        }
        setModificationTimes(analyserContext, modificationTimeIncrements, statementTimeDelta, currentModificationTimes);
        return new ProgressAndDelay(progress, causesOfDelay);
    }

    private Map<Variable, DV> computeFinalModified(Map<Variable, DV> propertyMap,
                                                   CausesOfDelay extraDelayIn,
                                                   boolean noAssignments) {
        Map<Variable, DV> finalModified;
        if (noAssignments && propertyMap.values().stream().allMatch(DV::valueIsFalse)) {
            finalModified = propertyMap;
            assert propertyMap.keySet().containsAll(variablesInClusters);
        } else {
            finalModified = new HashMap<>();
            for (Variable variable : variablesInClusters) {
                DV inPropertyMap = potentiallyBreakContextModifiedDelay(variable, propertyMap.get(variable));
                Map<Variable, DV> map = shortestPath.linksFollowIsHCOf(variable);

                DV max = map.values().stream().reduce(DelayFactory.initialDelay(), DV::max);
                CausesOfDelay clusterDelay = max.isInitialDelay() ? CausesOfDelay.EMPTY : max.causesOfDelay();
                CausesOfDelay notYetSet = this.linkingNotYetSet.contains(variable) ? LinkedVariables.NOT_YET_SET_DELAY
                        : CausesOfDelay.EMPTY;
                CausesOfDelay delays = clusterDelay.merge(extraDelayIn).merge(notYetSet);

                for (Variable reached : map.keySet()) {
                    if (reached == variable && inPropertyMap.valueIsTrue()) {
                        finalModified.put(reached, DV.TRUE_DV);
                    } else {
                        DV inFinal = finalModified.getOrDefault(reached, DV.FALSE_DV);
                        if (!inFinal.valueIsTrue()) {
                            if (delays.isDelayed()) {
                                // once true, always true; but one delay is a delay for everyone in the path
                                finalModified.put(reached, delays.merge(inFinal.causesOfDelay()));
                            } else if (inPropertyMap.valueIsTrue()) {
                                // non-delay linked to a TRUE, so this travels
                                finalModified.put(reached, DV.TRUE_DV);
                            } else {
                                // keep delays
                                DV combined = inPropertyMap.max(inFinal);
                                finalModified.put(reached, combined);
                            }
                        }
                    }
                }
            }
        }
        return finalModified;
    }

    /*
    All modification times are set to the max of the modification time of all dependent variables clustered,
    which are then (after taking the max) incremented by the max of their change data's increments.
    This will become the new value.

    TODO currently not too efficient
    */
    private void setModificationTimes(AnalyserContext analyserContext,
                                      Map<Variable, Integer> modificationTimeIncrements,
                                      int statementTimeDelta,
                                      Map<Variable, Integer> currentModificationTimes) {
        Map<Variable, Integer> modificationTimesToSet = new HashMap<>();
        for (Variable variable : variablesInClusters) {
            if (!modificationTimesToSet.containsKey(variable)) {
                VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(variable.fullyQualifiedName());
                if (vic != null) {
                    VariableInfo vi = vic.best(stage);
                    DV modified = vi.getProperty(Property.CONTEXT_MODIFIED);
                    int finalValue;
                    Map<Variable, DV> map = shortestPath.links(variable, LinkedVariables.LINK_DEPENDENT);
                    if (modified.isDelayed() || modificationDelayIn(map.keySet())) {
                        finalValue = -1;
                    } else {
                        int maxIncrement = 0;
                        for (Variable dependent : map.keySet()) {
                            int increment = modificationTimeIncrements.getOrDefault(dependent, 0);
                            maxIncrement = Math.max(maxIncrement, increment);
                        }
                        int maxInitial = currentModificationTimes.getOrDefault(variable, -1);
                        for (Variable dependent : map.keySet()) {
                            int current = currentModificationTimes.getOrDefault(dependent, -1);
                            if (current >= 0) {
                                maxInitial = Math.max(current, maxInitial);
                            }
                        }
                        finalValue = Math.max(0, maxInitial) + maxIncrement + statementTimeDelta;
                    }
                    for (Variable dependent : map.keySet()) {
                        modificationTimesToSet.putIfAbsent(dependent, finalValue);
                    }
                    modificationTimesToSet.put(variable, finalValue);
                }
            }
        }
        modificationTimesToSet.forEach((variable, timeIn) -> {
            int time = isComputedOrMutable(variable.parameterizedType(), analyserContext) ? timeIn : 0;
            if (time >= 0) {
                VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(variable.fullyQualifiedName());
                if (vic != null) {
                    vic.setModificationTimeIfNotYetSet(time, stage);
                }
            }
        });
    }

    private boolean modificationDelayIn(Set<Variable> variables) {
        return variables.stream().map(this::cm).reduce(DV.FALSE_DV, DV::max).isDelayed();
    }

    private DV cm(Variable v) {
        VariableInfoContainer vic = statementAnalysis.getVariableOrDefaultNull(v.fullyQualifiedName());
        if (vic == null) return DV.FALSE_DV;
        VariableInfo vi = vic.best(stage);
        return vi.getProperty(Property.CONTEXT_MODIFIED);
    }

    private boolean isComputedOrMutable(ParameterizedType parameterizedType, AnalyserContext analyserContext) {
        if (parameterizedType.isPrimitiveStringClass()) return false;
        TypeInfo bestType = parameterizedType.bestTypeInfo(analyserContext);
        if (bestType == null) return false; // locally immutable
        TypeAnalysis typeAnalysis = analyserContext.getTypeAnalysis(bestType);
        if (typeAnalysis.isComputed()) return true;
        DV immutable = typeAnalysis.getProperty(Property.IMMUTABLE);
        return immutable.isDelayed() || MultiLevel.isMutable(immutable);
    }

}
