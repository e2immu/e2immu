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

package org.e2immu.analyser.analysis.impl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.util.AnalyserComponents;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analysis.MethodLevelData;
import org.e2immu.analyser.analysis.StateData;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Identifier;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.SetOnce;
import org.e2immu.support.SetOnceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.util.EventuallyFinalExtension.setFinalAllowEquals;

/**
 * IMPORTANT:
 * Method level data is incrementally copied from one statement to the next.
 * The method analyser will only investigate the data from the last statement in the method!
 */
public class MethodLevelDataImpl implements MethodLevelData {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodLevelDataImpl.class);

    public static final String LINKS_HAVE_BEEN_ESTABLISHED = "linksHaveBeenEstablished";
    public static final String COMBINE_PRECONDITION = "combinePrecondition";
    public static final String COMBINE_POST_CONDITIONS = "combinePostConditions";
    public static final String COMBINE_ESCAPE_INDICES = "combineEscapeIndices";
    public static final String STATIC_SIDE_EFFECTS = "combineStaticSideEffects";

    private final SetOnceMap<MethodInfo, Boolean> copyModificationStatusFrom = new SetOnceMap<>();

    // aggregates the preconditions on individual statements
    private final EventuallyFinal<Precondition> combinedPrecondition = new EventuallyFinal<>();
    private final EventuallyFinal<Set<PostCondition>> postConditions = new EventuallyFinal<>();

    // not for local processing, but so that we know in the method and field analyser that this process has been completed
    private final EventuallyFinal<CausesOfDelay> linksHaveBeenEstablished = new EventuallyFinal<>();

    private final SetOnce<Set<String>> indicesOfEscapesNotInPreOrPostConditions = new SetOnce<>();

    private final EventuallyFinal<StaticSideEffects> staticSideEffects = new EventuallyFinal<>();

    public CausesOfDelay combinedPreconditionIsDelayedSet() {
        if (combinedPrecondition.isFinal()) return CausesOfDelay.EMPTY;
        Precondition cp = combinedPrecondition.get();
        if (cp == null) return null;
        CausesOfDelay causes = cp.expression().causesOfDelay();
        assert causes.isDelayed();
        return causes;
    }

    public CausesOfDelay linksHaveNotYetBeenEstablished() {
        return linksHaveBeenEstablished.get();
    }

    public Set<PostCondition> getPostConditions() {
        return Objects.requireNonNullElse(postConditions.get(), Set.of());
    }

    public boolean arePostConditionsDelayed() {
        return postConditions.isVariable();
    }

    public boolean combinedPreconditionIsFinal() {
        return combinedPrecondition.isFinal();
    }

    public Precondition combinedPreconditionGet() {
        return combinedPrecondition.get();
    }

    public void internalAllDoneCheck() {
        assert combinedPrecondition.isFinal();
        assert postConditions.isFinal();
        assert linksHaveBeenEstablished.isFinal();
    }

    public void makeUnreachable(Primitives primitives) {
        if (combinedPrecondition.isVariable()) combinedPrecondition.setFinal(Precondition.empty(primitives));
        if (postConditions.isVariable()) postConditions.setFinal(Set.of());
        if (linksHaveBeenEstablished.isVariable()) linksHaveBeenEstablished.setFinal(CausesOfDelay.EMPTY);
    }

    public record SharedState(AnalyserResult.Builder builder,
                              EvaluationResult context,
                              StatementAnalysis statementAnalysis,
                              String logLocation,
                              MethodLevelData previous,
                              String previousIndex,
                              StateData stateData) {
    }

    public final AnalyserComponents<String, SharedState> analyserComponents =
            new AnalyserComponents.Builder<String, SharedState>()
                    .add(LINKS_HAVE_BEEN_ESTABLISHED, this::linksHaveBeenEstablished)
                    .add(COMBINE_PRECONDITION, this::combinePrecondition)
                    .add(COMBINE_POST_CONDITIONS, this::combinePostConditions)
                    .add(COMBINE_ESCAPE_INDICES, this::combineEscapeIndices)
                    .add(STATIC_SIDE_EFFECTS, this::combineStaticSideEffects)
                    .build();


    public AnalysisStatus analyse(StatementAnalyserSharedState sharedState,
                                  StatementAnalysis statementAnalysis,
                                  MethodLevelData previous,
                                  String previousIndex,
                                  StateData stateData) {
        String logLocation = statementAnalysis.location(Stage.EVALUATION).toString();
        try {
            AnalyserResult.Builder builder = sharedState.builder();
            SharedState localSharedState = new SharedState(builder, sharedState.context(), statementAnalysis,
                    logLocation, previous, previousIndex, stateData);
            return analyserComponents.run(localSharedState);
        } catch (RuntimeException rte) {
            LOGGER.error("Caught exception in linking computation, {}", logLocation);
            throw rte;
        }
    }

    private AnalysisStatus combinePostConditions(SharedState sharedState) {
        Stream<PostCondition> fromMyStateData = Stream.of(sharedState.stateData.getPostCondition());

        Stream<PostCondition> fromPrevious = sharedState.previous != null ?
                sharedState.previous.getPostConditions().stream() : Stream.of();

        List<StatementAnalysis> subBlocks = sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks();
        Stream<PostCondition> fromBlocks = subBlocks.stream()
                .flatMap(sa -> sa.methodLevelData().getPostConditions().stream());

        Set<PostCondition> all = Stream.concat(fromPrevious, Stream.concat(fromMyStateData, fromBlocks))
                .filter(PostCondition::isNotEmpty)
                .collect(Collectors.toUnmodifiableSet());
        CausesOfDelay delays = all.stream().map(pc -> pc.expression().causesOfDelay())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (delays.isDelayed()) {
            postConditions.setVariable(all);
            return delays;
        }
        setFinalAllowEquals(postConditions, all);
        return DONE;
    }


    private AnalysisStatus combineEscapeIndices(SharedState sharedState) {
        String index = sharedState.stateData.isEscapeNotInPreOrPostConditions()
                ? sharedState.statementAnalysis.index() : null;
        Stream<String> fromMyStateData = Stream.ofNullable(index);

        Stream<String> fromPrevious = sharedState.previous != null ?
                sharedState.previous.getIndicesOfEscapesNotInPreOrPostConditions().stream() : Stream.of();

        List<StatementAnalysis> subBlocks = sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks();
        Stream<String> fromBlocks = subBlocks.stream()
                .flatMap(sa -> sa.methodLevelData().getIndicesOfEscapesNotInPreOrPostConditions().stream());

        Set<String> all = Stream.concat(fromPrevious, Stream.concat(fromMyStateData, fromBlocks))
                .collect(Collectors.toUnmodifiableSet());

        indicesOfEscapesNotInPreOrPostConditions.set(all);
        return DONE;
    }


    // preconditions come from the precondition expression in stateData
    // they are accumulated from the previous statement, and from all child statements
    private AnalysisStatus combinePrecondition(SharedState sharedState) {
        Stream<Precondition> fromMyStateData = Stream.of(sharedState.stateData.getPrecondition());

        Stream<Precondition> fromPrevious = sharedState.previous != null ?
                Stream.of(sharedState.previous.combinedPreconditionGet()) : Stream.of();

        List<StatementAnalysis> subBlocks = sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks();
        Stream<Precondition> fromBlocks = subBlocks.stream()
                .map(sa -> sa.methodLevelData().combinedPreconditionGet());

        Precondition empty = Precondition.empty(sharedState.context.getPrimitives());
        // order is important here, method calls remain in order given the same object
        Precondition all = Stream.concat(fromPrevious, Stream.concat(fromMyStateData, fromBlocks))
                .map(pc -> pc == null ? empty : pc)
                .reduce((pc1, pc2) -> pc1.combine(sharedState.context, pc2))
                .orElse(empty);

        if (all.isDelayed()) {
            combinedPrecondition.setVariable(all);
            return all.causesOfDelay();
        }

        assert Identifier.isListOfPositionalIdentifiers(all.expression());

        setFinalAllowEquals(combinedPrecondition, all);
        return DONE;
    }

    public boolean linksHaveBeenEstablished() {
        return linksHaveBeenEstablished.isFinal();
    }

    public CausesOfDelay getLinksHaveBeenEstablished() {
        return linksHaveBeenEstablished.get();
    }

    private AnalysisStatus linksHaveBeenEstablished(SharedState sharedState) {
        assert linksHaveBeenEstablished.isVariable();

        CausesOfDelay delayed = sharedState.statementAnalysis.variableStream()
                .filter(vi -> !(vi.variable() instanceof This))
                // local variables that have been created, but not yet assigned/read; reject ConditionalInitialization
                .filter(vi -> !(vi.variable() instanceof LocalVariableReference) || vi.isAssigned())
                // accept all linked variable delays, but not CM on the return variable (is not used anywhere)
                .map(vi -> vi.getLinkedVariables().causesOfDelay().merge(
                        vi.variable() instanceof ReturnVariable ? CausesOfDelay.EMPTY :
                                vi.getProperty(Property.CONTEXT_MODIFIED).causesOfDelay()))
                .filter(CausesOfDelay::isDelayed)
                .findFirst().orElse(null);
        // IMPORTANT! only the first delay is passed on, not all delays are computed
        if (delayed != null) {
            linksHaveBeenEstablished.setVariable(delayed);
            return delayed;
        }
        linksHaveBeenEstablished.setFinal(CausesOfDelay.EMPTY);
        return DONE;
    }

    public Set<String> getIndicesOfEscapesNotInPreOrPostConditions() {
        return indicesOfEscapesNotInPreOrPostConditions.getOrDefault(Set.of());
    }

    public boolean staticSideEffectsHaveBeenFound() {
        return staticSideEffects.isFinal();
    }

    public StaticSideEffects staticSideEffects() {
        return staticSideEffects.get();
    }

    private AnalysisStatus combineStaticSideEffects(SharedState sharedState) {
        assert staticSideEffects.isVariable();

        Expression current = sharedState.stateData.staticSideEffect();
        Stream<Expression> currentStream = current.isEmpty() ? Stream.of() : Stream.of(current);

        Stream<Expression> previouStream = sharedState.previous == null
                ? Stream.of() : sharedState.previous.staticSideEffects().expressions().stream();
        List<StatementAnalysis> subBlocks = sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks();
        Stream<Expression> subStream = subBlocks.stream()
                .flatMap(sa -> sa.methodLevelData().staticSideEffects().expressions().stream());

        List<Expression> newExpressions = Stream.concat(Stream.concat(currentStream, subStream), previouStream).toList();
        List<Expression> newExpressionsWithoutDelays = newExpressions.stream().filter(Expression::isDone).toList();

        if (!newExpressionsWithoutDelays.isEmpty()) {
            // shortcut: as soon as we have one, we don't need more
            staticSideEffects.setFinal(new StaticSideEffects(newExpressionsWithoutDelays));
            return DONE;
        }
        StaticSideEffects newSSE = new StaticSideEffects(newExpressions);
        CausesOfDelay causesOfDelay = newSSE.causesOfDelay();
        if (causesOfDelay.isDone()) {
            staticSideEffects.setFinal(newSSE);
            return DONE;
        }
        staticSideEffects.setVariable(newSSE);
        return causesOfDelay;
    }

    @Override
    public Stream<MethodInfo> copyModificationStatusFromKeyStream() {
        return copyModificationStatusFrom.keyStream();
    }

    @Override
    public void warnAboutAnalyserComponents(String statementIndex, String methodFqn) {
        LOGGER.warn("Analyser components of method level data of last statement {} of {}:\n{}", statementIndex,
                methodFqn, analyserComponents.details());
    }
}
