/*
 * e2immu: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.LogTarget.LINKED_VARIABLES;
import static org.e2immu.analyser.util.Logger.log;

/**
 * IMPORTANT:
 * Method level data is incrementally copied from one statement to the next.
 * The method analyser will only investigate the data from the last statement in the method!
 */
public class MethodLevelData {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodLevelData.class);

    // part of modification status for methods dealing with SAMs
    private final SetOnce<Boolean> callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod = new SetOnce<>();

    public Boolean getCallsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod() {
        return callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.getOrElse(null);
    }

    public final SetOnceMap<MethodInfo, Boolean> copyModificationStatusFrom = new SetOnceMap<>();

    // aggregates the preconditions on individual statements
    private final SetOnce<Expression> combinedPrecondition = new SetOnce<>();
    private Expression currentDelayedCombinedPrecondition;

    // no delays when frozen
    private final AddOnceSet<ObjectFlow> internalObjectFlows = new AddOnceSet<>();

    // not for local processing, but so that we know in the method and field analyser that this process has been completed
    public final FlipSwitch linksHaveBeenEstablished = new FlipSwitch();

    public Expression getCombinedPrecondition() {
        return combinedPrecondition.getOrElse(null);
    }

    public Expression getCombinedPreconditionOrDelay() {
        return combinedPrecondition.getOrElse(currentDelayedCombinedPrecondition);
    }

    private void setCombinedPrecondition(Expression expression, boolean isDelayed) {
        if (isDelayed) {
            currentDelayedCombinedPrecondition = expression;
        } else if (!combinedPrecondition.isSet() || !combinedPrecondition.get().equals(expression)) {
            combinedPrecondition.set(expression);
        }
    }

    public void addCircularCallOrUndeclaredFunctionalInterface() {
        if (!callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.isSet()) {
            callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.set(true);
        }
    }

    public Stream<ObjectFlow> getInternalObjectFlowStream() {
        return internalObjectFlows.stream();
    }

    public boolean internalObjectFlowNotYetFrozen() {
        return !internalObjectFlows.isFrozen();
    }

    public void freezeInternalObjectFlows() {
        internalObjectFlows.freeze();
    }

    public void ensureInternalObjectFlow(ObjectFlow objectFlow) {
        if (!internalObjectFlows.contains(objectFlow)) {
            internalObjectFlows.add(objectFlow);
        }
    }

    public boolean combinedPreconditionIsSet() {
        return combinedPrecondition.isSet();
    }

    public boolean combinedPreconditionIsDelayed() {
        return !combinedPrecondition.isSet();
    }

    record SharedState(StatementAnalyserResult.Builder builder,
                       EvaluationContext evaluationContext,
                       StatementAnalysis statementAnalysis,
                       String logLocation,
                       MethodLevelData previous,
                       StateData stateData) {
    }

    public final AnalyserComponents<String, SharedState> analyserComponents = new AnalyserComponents.Builder<String, SharedState>()
            .add("ensureThisProperties", sharedState -> ensureThisProperties())
            .add("linksHaveBeenEstablished", this::linksHaveBeenEstablished)
            .add("combinePrecondition", this::combinePrecondition)
            .build();

    public AnalysisStatus analyse(StatementAnalyser.SharedState sharedState,
                                  StatementAnalysis statementAnalysis,
                                  MethodLevelData previous,
                                  StateData stateData) {
        EvaluationContext evaluationContext = sharedState.evaluationContext();
        String logLocation = statementAnalysis.location().toString();
        try {
            StatementAnalyserResult.Builder builder = sharedState.builder();
            SharedState localSharedState = new SharedState(builder, evaluationContext, statementAnalysis, logLocation, previous, stateData);
            return analyserComponents.run(localSharedState);
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in linking computation, {}", logLocation);
            throw rte;
        }
    }

    // preconditions come from the precondition expression in stateData
    // they are accumulated from the previous statement, and from all child statements

    private AnalysisStatus combinePrecondition(SharedState sharedState) {
        boolean delays = sharedState.previous != null && !sharedState.previous.combinedPrecondition.isSet();

        List<StatementAnalysis> subBlocks = sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks();
        delays |= subBlocks.stream().anyMatch(sa -> !sa.methodLevelData.combinedPrecondition.isSet());
        delays |= sharedState.stateData.preconditionIsDelayed();

        Stream<Expression> fromMyStateData = sharedState.stateData.preconditionIsSet() ?
                Stream.of(sharedState.stateData.getPrecondition()) : Stream.of();
        Stream<Expression> fromPrevious = sharedState.previous != null && sharedState.previous.getCombinedPrecondition() != null ?
                Stream.of(sharedState.previous.getCombinedPrecondition()) : Stream.of();
        Stream<Expression> fromBlocks = sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks().stream()
                .map(sa -> sa.methodLevelData.getCombinedPrecondition())
                .filter(Objects::nonNull);
        Expression[] all = Stream.concat(fromMyStateData, Stream.concat(fromBlocks, fromPrevious)).toArray(Expression[]::new);
        Expression and = new And(sharedState.evaluationContext.getPrimitives()).append(sharedState.evaluationContext, all);

        delays |= sharedState.evaluationContext.isDelayed(and);
        setCombinedPrecondition(and, delays);

        return delays ? DELAYS : DONE;
    }

    private static final Variable DELAY_VAR = Variable.fake();

    private AnalysisStatus linksHaveBeenEstablished(SharedState sharedState) {
        assert !linksHaveBeenEstablished.isSet();

        boolean delays = sharedState.statementAnalysis.variableStream()
                .filter(variableInfo -> !(variableInfo.variable() instanceof This))
                .anyMatch(vi -> !vi.linkedVariablesIsSet() || vi.getProperty(VariableProperty.CONTEXT_MODIFIED) == Level.DELAY);
        if (delays) return DELAYS;
        linksHaveBeenEstablished.set();
        return DONE;
    }

    /**
     * @param statementAnalysis the statement
     * @param evaluationContext the eval context, used for creating EVAL level if needed
     * @param connections       either getLinkedVariables, or getStaticallyAssignedVariables
     * @return status
     */
    public static AnalysisStatus contextProperty(StatementAnalysis statementAnalysis,
                                                 EvaluationContext evaluationContext,
                                                 Function<VariableInfo, LinkedVariables> connections,
                                                 VariableProperty variableProperty,
                                                 Map<Variable, Integer> propertyValues,
                                                 VariableInfoContainer.Level level) {
        final AtomicReference<AnalysisStatus> analysisStatus = new AtomicReference<>(DONE);
        final DependencyGraph<Variable> dependencyGraph = new DependencyGraph<>();
        final boolean bidirectional = variableProperty == VariableProperty.CONTEXT_NOT_NULL;

        // delays in dependency graph
        statementAnalysis.variableStream(level)
                .forEach(variableInfo -> {
                    LinkedVariables linkedVariables = connections.apply(variableInfo);
                    if (linkedVariables == LinkedVariables.DELAY) {
                        if (!(variableInfo.variable() instanceof LocalVariableReference) || variableInfo.isAssigned()) {
                            log(DELAYED, "Delaying {} in MethodLevelData for {} in {}: linked variables not set",
                                    variableInfo, variableInfo.variable().fullyQualifiedName(), evaluationContext.getLocation());
                            analysisStatus.set(DELAYS);
                            dependencyGraph.addNode(variableInfo.variable(), Set.of(DELAY_VAR), bidirectional);
                        } else {
                            log(LINKED_VARIABLES, "Local variable {} not yet assigned, so cannot yet be linked ({})",
                                    variableInfo.variable().fullyQualifiedName(), variableProperty);
                        }
                    } else {
                        dependencyGraph.addNode(variableInfo.variable(), linkedVariables.variables(), bidirectional);
                    }
                });
        if (analysisStatus.get() == DELAYS) {
            // to make sure that the delay var is there too, in the unidirectional case
            dependencyGraph.addNode(DELAY_VAR, Set.of(), bidirectional);
        }
        final AtomicBoolean progress = new AtomicBoolean();

        // we make a copy of the values, because in summarizeModification there is the possibility of adding to the map
        Map<VariableInfoContainer, Integer> valuesToSet = new HashMap<>();

        // NOTE: this used to be safeVariableStream but don't think that is needed anymore
        statementAnalysis.variableStream(level)
                .forEach(variableInfo -> {
                    Variable baseVariable = variableInfo.variable();
                    Set<Variable> variablesBaseLinksTo =
                            Stream.concat(Stream.of(baseVariable), dependencyGraph.dependencies(baseVariable).stream())
                                    .filter(v -> v == DELAY_VAR || statementAnalysis.variables.isSet(v.fullyQualifiedName()))
                                    .collect(Collectors.toSet());
                    boolean containsDelayVar = variablesBaseLinksTo.stream().anyMatch(v -> v == DELAY_VAR);
                    if (!containsDelayVar) {
                        int summary = summarizeContext(variablesBaseLinksTo, variableProperty, propertyValues);
                        if (summary == Level.DELAY) analysisStatus.set(DELAYS);
                        // this loop is critical, see Container_3, do not remove it again :-)
                        for (Variable linkedVariable : variablesBaseLinksTo) {
                            assignToLinkedVariable(statementAnalysis, evaluationContext, progress, summary, linkedVariable,
                                    variableProperty, level, valuesToSet);
                        }
                    }
                });
        valuesToSet.forEach((k, v) -> {
            if (v != Level.DELAY) {
                k.setProperty(variableProperty, v, level);
            }
        });
        return analysisStatus.get() == DELAYS ? (progress.get() ? PROGRESS : DELAYS) : DONE;
    }

    private static int summarizeContext(Set<Variable> linkedVariables,
                                        VariableProperty variableProperty,
                                        Map<Variable, Integer> values) {
        boolean hasDelays = false;
        int max = Level.DELAY;
        for (Variable variable : linkedVariables) {
            Integer modified = values.get(variable);
            if (modified == null) {
                throw new NullPointerException("Expect " + variable.fullyQualifiedName() + " to be known for "
                        + variableProperty + ", map is " + values);
            }
            if (modified == Level.DELAY) hasDelays = true;
            max = Math.max(max, modified);
        }
        return hasDelays && max < variableProperty.best ? Level.DELAY : max;
    }

    private static void assignToLinkedVariable(StatementAnalysis statementAnalysis,
                                               EvaluationContext evaluationContext,
                                               AtomicBoolean progress,
                                               int summary,
                                               Variable linkedVariable,
                                               VariableProperty variableProperty,
                                               VariableInfoContainer.Level level,
                                               Map<VariableInfoContainer, Integer> valuesToSet) {
        VariableInfoContainer vic = statementAnalysis.variables.get(linkedVariable.fullyQualifiedName());
        VariableInfo vi1 = level == VariableInfoContainer.Level.EVALUATION ? vic.getPreviousOrInitial() : vic.current();
        ensureLevel(statementAnalysis, evaluationContext, vic, vi1, level);
        VariableInfo vi = vic.best(level);
        int modified = vi.getProperty(variableProperty);
        if (modified == Level.DELAY) {
            // break the delay in case the variable is not even read

            if (summary != Level.DELAY) {
                // once delay, always delay
                valuesToSet.merge(vic, summary, (v1, v2) -> v1 == Level.DELAY ? Level.DELAY : Math.max(v1, v2));
                progress.set(true);
            } else {
                valuesToSet.put(vic, Level.DELAY);
            }
        } else if (modified != summary && summary != Level.DELAY) {
            throw new UnsupportedOperationException("? already have " + modified + ", computed "
                    + summary + " variable " + vi.variable().fullyQualifiedName() + ", prop " + variableProperty);
        }
    }

    private static void ensureLevel(StatementAnalysis statementAnalysis,
                                    EvaluationContext evaluationContext,
                                    VariableInfoContainer vic,
                                    VariableInfo vi,
                                    VariableInfoContainer.Level level) {
        if (level.equals(VariableInfoContainer.Level.EVALUATION)) {
            if (!vic.hasMerge() && !vic.hasEvaluation()) {
                int statementTimeForVariable = statementAnalysis.statementTimeForVariable(evaluationContext.getAnalyserContext(),
                        vi.variable(), evaluationContext.getInitialStatementTime());

                vic.ensureEvaluation(VariableInfoContainer.NOT_YET_ASSIGNED,
                        VariableInfoContainer.NOT_YET_ASSIGNED,
                        statementTimeForVariable, Set.of());
                vic.setValue(vi.getValue(), vi.isDelayed(), LinkedVariables.EMPTY, vi.getProperties().toImmutableMap(), false);
                if (vi.linkedVariablesIsSet()) vic.setLinkedVariables(vi.getLinkedVariables(), false);
            }
        } else {
            if (!vic.hasMerge()) throw new UnsupportedOperationException("NYI -- but is this possible?");
        }
    }

    /**
     * Finish odds and ends
     *
     * @return if any change happened to methodAnalysis
     */
    private AnalysisStatus ensureThisProperties() {
        if (!callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.isSet()) {
            callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.set(false);
        }
        return DONE;
    }
}
