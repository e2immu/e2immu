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

import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.FlipSwitch;
import org.e2immu.support.SetOnce;
import org.e2immu.support.SetOnceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.util.EventuallyFinalExtension.setFinalAllowEquals;
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

    public static final String MERGE_CAUSES_OF_CONTEXT_MODIFICATION_DELAY = "mergeCausesOfContextModificationDelay";
    public static final String ENSURE_THIS_PROPERTIES = "ensureThisProperties";
    public static final String LINKS_HAVE_BEEN_ESTABLISHED = "linksHaveBeenEstablished";
    public static final String COMBINE_PRECONDITION = "combinePrecondition";

    private static final int ITERATIONS_TO_WAIT = 2;

    // part of modification status for dealing with circular methods
    private final SetOnce<Boolean> callsPotentiallyCircularMethod = new SetOnce<>();

    public DV getCallsPotentiallyCircularMethod() {
        return callsPotentiallyCircularMethod.getOrDefaultNull();
    }

    public final SetOnceMap<MethodInfo, Boolean> copyModificationStatusFrom = new SetOnceMap<>();

    // aggregates the preconditions on individual statements
    public final EventuallyFinal<Precondition> combinedPrecondition = new EventuallyFinal<>();

    // not for local processing, but so that we know in the method and field analyser that this process has been completed
    public final FlipSwitch linksHaveBeenEstablished = new FlipSwitch();

    /*
    The map causesOfContextModificationDelay is essentially a set which records which objects were the cause of a context modification
    delay. It is used to break infinite delay loops.
    The eventually final object becomes final when the set is empty: there are no causes of delay anymore.
    One by one, the causes of the delay get removed until the set is empty.
    The reason it is a set and not a counter is that accounting is not that simple in a multi-pass system.
    It is safe to remove the same object twice; with a counter, this is much more tricky.

    The reason it is a map (rather than a set) is that we delay removal by a fixed number of iterations: the removal is necessary
    to break delays in one direction (Modification_20) but the removal cannot take place too quickly (Modification_21).
    IMPROVE Rather than having a fixed number of iterations between marking the removal and actually removing, we should
    find a better, more definitive solution.
     */
    private final EventuallyFinal<Map<WithInspectionAndAnalysis, Integer>> causesOfContextModificationDelay = new EventuallyFinal<>();

    public void addCircularCall() {
        if (!callsPotentiallyCircularMethod.isSet()) {
            callsPotentiallyCircularMethod.set(true);
        }
    }

    public CausesOfDelay combinedPreconditionIsDelayedSet() {
        if (combinedPrecondition.isFinal()) return null;
        Precondition cp = combinedPrecondition.get();
        if (cp == null) return Set.of();
        return combinedPrecondition.get().expression().variables().stream().collect(Collectors.toUnmodifiableSet());
    }

    public boolean causesOfContextModificationDelayIsVariable() {
        return causesOfContextModificationDelay.isVariable();
    }

    public void causesOfContextModificationDelayAddVariable(Map<WithInspectionAndAnalysis, Boolean> map, boolean allowRemoval) {
        assert causesOfContextModificationDelay.isVariable();
        if (causesOfContextModificationDelay.get() == null) {
            causesOfContextModificationDelay.setVariable(new HashMap<>());
        }
        Map<WithInspectionAndAnalysis, Integer> causes = causesOfContextModificationDelay.get();
        map.forEach((k, v) -> {
            if (v) causes.put(k, ITERATIONS_TO_WAIT);
            else if (allowRemoval) {
                Integer count = causes.get(k);
                if (count != null) {
                    if (count == 0) causes.remove(k);
                    else causes.put(k, count - 1);
                }
            }
        });
    }

    public void causesOfContextModificationDelaySetFinal() {
        causesOfContextModificationDelay.setFinal(Map.of());
    }

    public Set<WithInspectionAndAnalysis> getCausesOfContextModificationDelay() {
        return causesOfContextModificationDelay.get().keySet();
    }

    public CausesOfDelay linksHaveNotYetBeenEstablished(Predicate<WithInspectionAndAnalysis> canBeIgnored) {
        if (linksHaveBeenEstablished.isSet()) return false;
        Map<WithInspectionAndAnalysis, Integer> causes = causesOfContextModificationDelay.get();
        if (causes != null && !causes.isEmpty() && causes.keySet().stream().allMatch(canBeIgnored)) {
            log(LINKED_VARIABLES, "Accepting a limited version of linksHaveBeenEstablished to break delay cycle");
            return false;
        }
        return true;
    }

    record SharedState(StatementAnalyserResult.Builder builder,
                       EvaluationContext evaluationContext,
                       StatementAnalysis statementAnalysis,
                       String logLocation,
                       MethodLevelData previous,
                       String previousIndex,
                       StateData stateData) {
        String where(String component) {
            return statementAnalysis.methodAnalysis.getMethodInfo().fullyQualifiedName
                    + ":" + statementAnalysis.index + ":MLD:" + component;
        }

        String myStatement(String index) {
            return statementAnalysis.methodAnalysis.getMethodInfo().fullyQualifiedName + ":" + index;
        }

        String myStatement() {
            return statementAnalysis.methodAnalysis.getMethodInfo().fullyQualifiedName + ":" +
                    statementAnalysis.index;
        }
    }

    public final AnalyserComponents<String, SharedState> analyserComponents = new AnalyserComponents.Builder<String, SharedState>()
            .add(MERGE_CAUSES_OF_CONTEXT_MODIFICATION_DELAY, this::mergeCausesOfContextModificationDelay)
            .add(ENSURE_THIS_PROPERTIES, sharedState -> ensureThisProperties())
            .add(LINKS_HAVE_BEEN_ESTABLISHED, this::linksHaveBeenEstablished)
            .add(COMBINE_PRECONDITION, this::combinePrecondition)
            .build();

    private AnalysisStatus mergeCausesOfContextModificationDelay(SharedState sharedState) {
        if (causesOfContextModificationDelay.isFinal()) return DONE;
        if (sharedState.previous != null && sharedState.previous.causesOfContextModificationDelay.isVariable()) {
            if (causesOfContextModificationDelay.get() == null) {
                causesOfContextModificationDelay.setVariable(new HashMap<>());
            }
            boolean added =
                    sharedState.previous.causesOfContextModificationDelay.get().entrySet().stream()
                            .map(e -> causesOfContextModificationDelay.get().put(e.getKey(), e.getValue()))
                            .reduce(false, (i, resultOfPut) -> resultOfPut == null, (a, b) -> a || b);
            assert !added || sharedState.previous.causesOfContextModificationDelay.get().keySet().stream().allMatch(cause ->
                    foundDelay(sharedState.where(MERGE_CAUSES_OF_CONTEXT_MODIFICATION_DELAY),
                            cause.fullyQualifiedName() + D_CAUSES_OF_CONTENT_MODIFICATION_DELAY));
        }
        sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks().stream()
                .filter(sa -> sa.methodLevelData.causesOfContextModificationDelay.get() != null)
                .flatMap(sa -> sa.methodLevelData.causesOfContextModificationDelay.get().entrySet().stream())
                .forEach(set -> {
                    if (set != null) {
                        Integer prev = causesOfContextModificationDelay.get().put(set.getKey(), set.getValue());
                        assert prev == null || foundDelay(sharedState.where(MERGE_CAUSES_OF_CONTEXT_MODIFICATION_DELAY),
                                set.getKey().fullyQualifiedName() + D_CAUSES_OF_CONTENT_MODIFICATION_DELAY);
                    }
                });
        if (causesOfContextModificationDelay.get() == null || causesOfContextModificationDelay.get().isEmpty()) {
            causesOfContextModificationDelaySetFinal();
            return DONE;
        }
        log(DELAYED, "Still have causes of context modification delay: {}",
                causesOfContextModificationDelay.get());
        return DELAYS;
    }

    public AnalysisStatus analyse(StatementAnalyser.SharedState sharedState,
                                  StatementAnalysis statementAnalysis,
                                  MethodLevelData previous,
                                  String previousIndex,
                                  StateData stateData) {
        EvaluationContext evaluationContext = sharedState.evaluationContext();
        String logLocation = statementAnalysis.location().toString();
        try {
            StatementAnalyserResult.Builder builder = sharedState.builder();
            SharedState localSharedState = new SharedState(builder, evaluationContext, statementAnalysis,
                    logLocation, previous, previousIndex, stateData);
            return analyserComponents.run(localSharedState);
        } catch (RuntimeException rte) {
            LOGGER.warn("Caught exception in linking computation, {}", logLocation);
            throw rte;
        }
    }


    // preconditions come from the precondition expression in stateData
    // they are accumulated from the previous statement, and from all child statements
    private AnalysisStatus combinePrecondition(SharedState sharedState) {
        boolean previousDelayed = sharedState.previous != null && sharedState.previous.combinedPrecondition.isVariable();
        assert !previousDelayed || foundDelay(sharedState.where(COMBINE_PRECONDITION),
                sharedState.myStatement(sharedState.previousIndex) + D_COMBINED_PRECONDITION);

        List<StatementAnalysis> subBlocks = sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks();
        Optional<StatementAnalysis> subBlockDelay = subBlocks.stream()
                .filter(sa -> sa.methodLevelData.combinedPrecondition.isVariable()).findFirst();
        assert subBlockDelay.isEmpty() || foundDelay(sharedState.where(COMBINE_PRECONDITION),
                sharedState.myStatement(subBlockDelay.get().index) + D_COMBINED_PRECONDITION);

        boolean preconditionFinal = sharedState.stateData.preconditionIsFinal();
        assert preconditionFinal || translatedDelay(sharedState.where(COMBINE_PRECONDITION),
                sharedState.myStatement() + D_PRECONDITION,
                sharedState.myStatement() + D_COMBINED_PRECONDITION);

        Stream<Precondition> fromMyStateData =
                Stream.of(sharedState.stateData.getPrecondition());
        Stream<Precondition> fromPrevious = sharedState.previous != null ?
                Stream.of(sharedState.previous.combinedPrecondition.get()) : Stream.of();
        Stream<Precondition> fromBlocks = sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks().stream()
                .map(sa -> sa.methodLevelData.combinedPrecondition)
                .map(EventuallyFinal::get);
        Precondition all = Stream.concat(fromMyStateData, Stream.concat(fromBlocks, fromPrevious))
                .map(pc -> pc == null ? Precondition.empty(sharedState.evaluationContext.getPrimitives()) : pc)
                .reduce((pc1, pc2) -> pc1.combine(sharedState.evaluationContext, pc2))
                .orElse(Precondition.empty(sharedState.evaluationContext.getPrimitives()));

        boolean allDelayed = sharedState.evaluationContext.isDelayed(all.expression());

        // I wonder whether it is possible that the combination is delayed when none of the constituents are?
        assert !allDelayed || createDelay(sharedState.where(COMBINE_PRECONDITION),
                sharedState.myStatement() + D_COMBINED_PRECONDITION);

        boolean delay = previousDelayed || subBlockDelay.isPresent() || !preconditionFinal || allDelayed;
        if (delay) {
            combinedPrecondition.setVariable(all);
            return DELAYS;
        }

        setFinalAllowEquals(combinedPrecondition, all);
        return DONE;
    }

    private AnalysisStatus linksHaveBeenEstablished(SharedState sharedState) {
        assert !linksHaveBeenEstablished.isSet();

        CausesOfDelay delayed = sharedState.statementAnalysis.variableStream()
                .filter(vi -> !(vi.variable() instanceof This))
                // local variables that have been created, but not yet assigned/read; reject ConditionalInitialization
                .filter(vi -> !(vi.variable() instanceof LocalVariableReference) || vi.isAssigned())
                // FIXME break immutable removed temporarily
                .map(vi -> vi.getLinkedVariables().causesOfDelay().merge(
                        vi.getProperty(VariableProperty.CONTEXT_MODIFIED).causesOfDelay()))
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (delayed.isDelayed()) {
            return new AnalysisStatus.Delayed(delayed);
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
        if (!callsPotentiallyCircularMethod.isSet()) {
            callsPotentiallyCircularMethod.set(false);
        }
        return DONE;
    }
}
