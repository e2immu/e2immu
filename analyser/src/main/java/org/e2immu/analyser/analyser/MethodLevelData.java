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

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
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
    public final SetOnce<Expression> combinedPrecondition = new SetOnce<>();

    // no delays when frozen
    public final AddOnceSet<ObjectFlow> internalObjectFlows = new AddOnceSet<>();

    // not for local processing, but so that we know in the method and field analyser that this process has been completed
    public final FlipSwitch linksHaveBeenEstablished = new FlipSwitch();

    public Expression getCombinedPrecondition() {
        return combinedPrecondition.getOrElse(null);
    }

    public Expression getCombinedPreconditionOrDelay() {
        return combinedPrecondition.getOrElse(EmptyExpression.NO_VALUE);
    }

    public void addCircularCallOrUndeclaredFunctionalInterface() {
        if (!callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.isSet()) {
            callsUndeclaredFunctionalInterfaceOrPotentiallyCircularMethod.set(true);
        }
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
        if (sharedState.previous != null && !sharedState.previous.combinedPrecondition.isSet()) {
            return DELAYS;
        }
        List<StatementAnalysis> subBlocks = sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks();
        if (subBlocks.stream().anyMatch(sa -> !sa.methodLevelData.combinedPrecondition.isSet())) {
            return DELAYS;
        }
        if (!sharedState.stateData.preconditionIsSet()) {
            return DELAYS;
        }
        if (!combinedPrecondition.isSet()) {
            Stream<Expression> fromMyStateData = sharedState.stateData.preconditionIsSet() ?
                    Stream.of(sharedState.stateData.getPrecondition()) : Stream.of();
            Stream<Expression> fromPrevious = sharedState.previous != null ?
                    Stream.of(sharedState.previous.combinedPrecondition.get()) : Stream.of();
            Stream<Expression> fromBlocks = sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks().stream()
                    .map(sa -> sa.methodLevelData.combinedPrecondition.get());
            Expression[] all = Stream.concat(fromMyStateData, Stream.concat(fromBlocks, fromPrevious)).toArray(Expression[]::new);
            Expression and = new And(sharedState.evaluationContext.getPrimitives()).append(sharedState.evaluationContext, all);
            combinedPrecondition.set(and);
        }
        return DONE;
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
                        int summary = sharedState.evaluationContext.summarizeModification(variablesBaseLinksTo);

                        // this loop is critical, see Container_3, do not remove it again :-)
                        for (Variable linkedVariable : variablesBaseLinksTo) {
                            assignToLinkedVariable(sharedState, analysisStatus, progress, summary, linkedVariable);
                        }
                    }
                });
        if (analysisStatus.get() == DONE) {
            linksHaveBeenEstablished.set();
        }
        return analysisStatus.get() == DELAYS ? (progress.get() ? PROGRESS : DELAYS) : DONE;
    }

    private void assignToLinkedVariable(SharedState sharedState,
                                        AtomicReference<AnalysisStatus> analysisStatus,
                                        AtomicBoolean progress,
                                        int summary,
                                        Variable linkedVariable) {
        if (linkedVariable instanceof FieldReference fieldReference) {
            // NOTE: not redirecting to "raw" field, via fieldReference.fieldInfo.fullyQualifiedName
            VariableInfo vi = sharedState.statementAnalysis.variables.get(fieldReference.fullyQualifiedName()).current();
            int modified = vi.getProperty(VariableProperty.MODIFIED);
            // the second condition is there because fields start with modified 0 in a method
            if (modified == Level.DELAY || modified < summary) {
                // break the delay in case the variable is not even read
                int fieldModified;
                if (summary == Level.DELAY && !vi.isRead()) {
                    fieldModified = Level.FALSE;
                } else fieldModified = summary;
                if (fieldModified == Level.DELAY) {
                    log(DELAYED, "Delay marking {} as @NotModified in {}", linkedVariable.fullyQualifiedName(), sharedState.logLocation);
                    analysisStatus.set(DELAYS);
                } else {
                    log(NOT_MODIFIED, "Mark {} " + (fieldModified == Level.TRUE ? "" : "NOT") + " @Modified in {}",
                            linkedVariable.fullyQualifiedName(), sharedState.logLocation);
                    ensureEvaluationAndSetModified(sharedState, vi, fieldModified);
                    progress.set(true);
                }
            }
            return;
        }
        if (linkedVariable instanceof ParameterInfo) {
            ParameterAnalysis parameterAnalysis = sharedState.evaluationContext.getAnalyserContext().getParameterAnalysis((ParameterInfo) linkedVariable);
            if (summary == Level.DELAY) {
                log(DELAYED, "Delay marking {} as @NotModified in {}", linkedVariable.fullyQualifiedName(), sharedState.logLocation);
                analysisStatus.set(DELAYS);
            } else {
                log(NOT_MODIFIED, "MethodLevelData: Mark {} as {} in {}", linkedVariable.fullyQualifiedName(),
                        summary == Level.TRUE ? "@Modified" : "@NotModified", sharedState.logLocation);
                int currentModified = parameterAnalysis.getProperty(VariableProperty.MODIFIED);
                if (currentModified < summary) {
                    // we can safely cast here to the builder
                    ParameterAnalysisImpl.Builder builder = (ParameterAnalysisImpl.Builder) parameterAnalysis;
                    sharedState.builder.add(builder.new SetProperty(VariableProperty.MODIFIED, summary));
                    progress.set(true);
                }
            }
        }
    }

    private void ensureEvaluationAndSetModified(SharedState sharedState, VariableInfo vi, int modified) {
        VariableInfoContainer vic = sharedState.statementAnalysis.findForWriting(vi.variable());
        if (!vic.hasMerge() && !vic.hasEvaluation()) {
            vic.ensureEvaluation(VariableInfoContainer.NOT_YET_ASSIGNED,
                    sharedState.statementAnalysis.index + VariableInfoContainer.Level.EVALUATION.label,
                    sharedState.evaluationContext.getInitialStatementTime(), Set.of());
            if (vi.valueIsSet()) vic.setValue(vi.getValue(), vi.getProperties(), false);
            if (vi.linkedVariablesIsSet()) vic.setLinkedVariables(vi.getLinkedVariables(), false);
        }
        vic.setProperty(VariableProperty.MODIFIED, modified, false, VariableInfoContainer.Level.MERGE);
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
