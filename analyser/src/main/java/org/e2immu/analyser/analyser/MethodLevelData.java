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
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.util.Logger.LogTarget.*;
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
            .add("computeContentModifications", this::computeContentModifications)
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

    private AnalysisStatus computeContentModifications(SharedState sharedState) {
        assert !linksHaveBeenEstablished.isSet();

        final AtomicReference<AnalysisStatus> analysisStatus = new AtomicReference<>(DONE);
        final DependencyGraph<Variable> dependencyGraph = new DependencyGraph<>();

        // delays in dependency graph
        sharedState.statementAnalysis.variableStream()
                .filter(variableInfo -> !(variableInfo.variable() instanceof This))
                .forEach(variableInfo -> {
                    LinkedVariables linkedVariables = variableInfo.getLinkedVariables();
                    /*
                    We should not do this
                    if (linkedVariables == LinkedVariables.DELAY &&
                            variableInfo.getValue() instanceof VariableExpression redirect) {
                        linkedVariables = sharedState.statementAnalysis.findOrThrow(redirect.variable()).getLinkedVariables();
                    }*/
                    if (linkedVariables == LinkedVariables.DELAY) {
                        if (!(variableInfo.variable() instanceof LocalVariableReference) || variableInfo.isAssigned()) {
                            log(DELAYED, "Delaying content modification in MethodLevelData for {} in {}: linked variables not set",
                                    variableInfo.variable().fullyQualifiedName(),
                                    sharedState.evaluationContext.getCurrentStatement());
                            analysisStatus.set(DELAYS);
                            dependencyGraph.addNode(variableInfo.variable(), Set.of(DELAY_VAR));
                        } else {
                            log(LINKED_VARIABLES, "Local variable {} not yet assigned, so cannot yet be linked",
                                    variableInfo.variable().fullyQualifiedName());
                        }
                    } else {
                        dependencyGraph.addNode(variableInfo.variable(), linkedVariables.variables());
                    }
                });
        if (analysisStatus.get() == DELAYS) {
            dependencyGraph.addNode(DELAY_VAR, Set.of()); // to make sure that the delay var is there too
        }
        final AtomicBoolean progress = new AtomicBoolean();

        // we make a copy of the values, because in summarizeModification there is the possibility of adding to the map
        Map<VariableInfoContainer, Integer> modifiedValuesToSet = new HashMap<>();

        sharedState.statementAnalysis.safeVariableStream()
                .filter(variableInfo -> !(variableInfo.variable() instanceof This))
                .forEach(variableInfo -> {
                    Variable baseVariable = variableInfo.variable();
                    //Set<Variable> variablesBaseLinksTo = SetUtil.immutableUnion(Set.of(baseVariable), dependencyGraph.dependencies(baseVariable));
                    Set<Variable> variablesBaseLinksTo =
                            Stream.concat(Stream.of(baseVariable), dependencyGraph.dependencies(baseVariable).stream())
                                    .filter(v -> v == DELAY_VAR || sharedState.statementAnalysis.variables.isSet(v.fullyQualifiedName()))
                                    .collect(Collectors.toSet());
                    boolean containsDelayVar = variablesBaseLinksTo.stream().anyMatch(v -> v == DELAY_VAR);
                    if (!containsDelayVar) {
                        int summary = sharedState.evaluationContext.summarizeContextModification(variablesBaseLinksTo);

                        // this loop is critical, see Container_3, do not remove it again :-)
                        for (Variable linkedVariable : variablesBaseLinksTo) {
                            if (assignToLinkedVariable(sharedState, progress, summary, linkedVariable, modifiedValuesToSet)) {
                                analysisStatus.set(DELAYS);
                            }
                        }
                    }
                });
        modifiedValuesToSet.forEach((k, v) -> k.setProperty(VariableProperty.CONTEXT_MODIFIED,
                v, false, VariableInfoContainer.Level.MERGE));
        if (analysisStatus.get() == DONE) {
            linksHaveBeenEstablished.set();
        }
        return analysisStatus.get() == DELAYS ? (progress.get() ? PROGRESS : DELAYS) : DONE;
    }

    private boolean assignToLinkedVariable(SharedState sharedState,
                                           AtomicBoolean progress,
                                           int summary,
                                           Variable linkedVariable,
                                           Map<VariableInfoContainer, Integer> modifiedValuesToSet) {

        VariableInfoContainer vic = sharedState.statementAnalysis.variables.get(linkedVariable.fullyQualifiedName());
        VariableInfo vi = vic.current();
        int modified = vi.getProperty(VariableProperty.CONTEXT_MODIFIED);
        if (modified == Level.DELAY || modified < summary) {
            // break the delay in case the variable is not even read
            int newModified;
            if (summary == Level.DELAY && !vi.isRead()) {
                newModified = Level.FALSE;
            } else newModified = summary;
            if (newModified == Level.DELAY) {
                log(DELAYED, "Delay marking field {} as @NotModified in {}", linkedVariable.fullyQualifiedName(), sharedState.logLocation);
                return true;
            }

            log(NOT_MODIFIED, "Mark {} " + (newModified == Level.TRUE ? "" : "NOT") + " @Modified in {}",
                    linkedVariable.fullyQualifiedName(), sharedState.logLocation);
            ensureEvaluation(sharedState, vic, vi);
            modifiedValuesToSet.merge(vic, newModified, Math::max);
            progress.set(true);
        }
        return false;
    }

    private void ensureEvaluation(SharedState sharedState, VariableInfoContainer vic, VariableInfo vi) {
        if (!vic.hasMerge() && !vic.hasEvaluation()) {
            int statementTimeForVariable = sharedState.statementAnalysis.statementTimeForVariable(
                    sharedState.evaluationContext.getAnalyserContext(),
                    vi.variable(), sharedState.evaluationContext.getInitialStatementTime());

            vic.ensureEvaluation(VariableInfoContainer.NOT_YET_ASSIGNED,
                    VariableInfoContainer.NOT_YET_ASSIGNED,
                    statementTimeForVariable, Set.of());
            vic.setValue(vi.getValue(), vi.isDelayed(), LinkedVariables.EMPTY, vi.getProperties().toImmutableMap(), false);
            if (vi.linkedVariablesIsSet()) vic.setLinkedVariables(vi.getLinkedVariables(), false);
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
