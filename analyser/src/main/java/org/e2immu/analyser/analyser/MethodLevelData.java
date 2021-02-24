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
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
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

    private AnalysisStatus linksHaveBeenEstablished(SharedState sharedState) {
        assert !linksHaveBeenEstablished.isSet();

        Optional<VariableInfo> delayed = sharedState.statementAnalysis.variableStream()
                .filter(vi -> !(vi.variable() instanceof This))
                // local variables that have been created, but not yet assigned/read
                .filter(vi -> !(vi.variable() instanceof LocalVariableReference) || vi.isAssigned() || vi.isRead())
                .filter(vi -> !vi.linkedVariablesIsSet() || vi.getProperty(VariableProperty.CONTEXT_MODIFIED) == Level.DELAY)
                .findFirst();
        if (delayed.isPresent()) {
            log(DELAYED, "Links have not yet been established for (findFirst) {}, statement {}",
                    delayed.get().variable().fullyQualifiedName(), sharedState.statementAnalysis.index);
            return DELAYS;
        }
        linksHaveBeenEstablished.set();
        return DONE;
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
