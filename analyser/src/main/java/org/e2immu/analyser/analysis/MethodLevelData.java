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

package org.e2immu.analyser.analysis;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.AnalyserProgram;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.WithInspectionAndAnalysis;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.SetOnce;
import org.e2immu.support.SetOnceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.util.EventuallyFinalExtension.setFinalAllowEquals;

/**
 * IMPORTANT:
 * Method level data is incrementally copied from one statement to the next.
 * The method analyser will only investigate the data from the last statement in the method!
 */
public class MethodLevelData {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodLevelData.class);

    public static final String ENSURE_THIS_PROPERTIES = "ensureThisProperties";
    public static final String LINKS_HAVE_BEEN_ESTABLISHED = "linksHaveBeenEstablished";
    public static final String COMBINE_PRECONDITION = "combinePrecondition";

    /* part of modification status for dealing with circular methods
       is computed during each evaluation, never delayed
     */
    private final SetOnce<Boolean> callsPotentiallyCircularMethod = new SetOnce<>();

    public boolean getCallsPotentiallyCircularMethod() {
        Boolean b = callsPotentiallyCircularMethod.getOrDefaultNull();
        if (b == null) throw new UnsupportedOperationException("Should have been computed already");
        return b;
    }

    public void addCircularCall() {
        if (!callsPotentiallyCircularMethod.isSet()) {
            callsPotentiallyCircularMethod.set(true);
        }
    }

    public final SetOnceMap<MethodInfo, Boolean> copyModificationStatusFrom = new SetOnceMap<>();

    // aggregates the preconditions on individual statements
    public final EventuallyFinal<Precondition> combinedPrecondition = new EventuallyFinal<>();

    // not for local processing, but so that we know in the method and field analyser that this process has been completed
    private final EventuallyFinal<CausesOfDelay> linksHaveBeenEstablished = new EventuallyFinal<>();

    public CausesOfDelay combinedPreconditionIsDelayedSet() {
        if (combinedPrecondition.isFinal()) return CausesOfDelay.EMPTY;
        Precondition cp = combinedPrecondition.get();
        if(cp == null) return null;
        return cp.expression().causesOfDelay();
    }


    public CausesOfDelay linksHaveNotYetBeenEstablished(Predicate<WithInspectionAndAnalysis> canBeIgnored) {
        return linksHaveBeenEstablished.get();
        /*
        if (linksHaveBeenEstablished.isSet()) return ;
        Map<WithInspectionAndAnalysis, Integer> causes = causesOfContextModificationDelay.get();
        if (causes != null && !causes.isEmpty() && causes.keySet().stream().allMatch(canBeIgnored)) {
            log(LINKED_VARIABLES, "Accepting a limited version of linksHaveBeenEstablished to break delay cycle");
            return false;
        }
        return true;

         */
    }

    public record SharedState(StatementAnalyserResult.Builder builder,
                              EvaluationContext evaluationContext,
                              StatementAnalysis statementAnalysis,
                              String logLocation,
                              MethodLevelData previous,
                              String previousIndex,
                              StateData stateData) {
    }

    public final AnalyserComponents<String, SharedState> analyserComponents =
         new AnalyserComponents.Builder<String, SharedState>(AnalyserProgram.PROGRAM_ALL)
                .add(ENSURE_THIS_PROPERTIES, sharedState -> ensureThisProperties())
                .add(LINKS_HAVE_BEEN_ESTABLISHED, this::linksHaveBeenEstablished)
                .add(COMBINE_PRECONDITION, this::combinePrecondition)
                .build();


    public AnalysisStatus analyse(StatementAnalyserSharedState sharedState,
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
        CausesOfDelay previousDelays = sharedState.previous == null ? CausesOfDelay.EMPTY :
                sharedState.previous.combinedPrecondition.get().expression().causesOfDelay();

        List<StatementAnalysis> subBlocks = sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks();
        CausesOfDelay subBlockDelays = subBlocks.stream()
                .map(sa -> sa.methodLevelData().combinedPrecondition.get().expression().causesOfDelay())
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);


        Stream<Precondition> fromMyStateData =
                Stream.of(sharedState.stateData.getPrecondition());
        Stream<Precondition> fromPrevious = sharedState.previous != null ?
                Stream.of(sharedState.previous.combinedPrecondition.get()) : Stream.of();
        Stream<Precondition> fromBlocks = sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks().stream()
                .map(sa -> sa.methodLevelData().combinedPrecondition)
                .map(EventuallyFinal::get);
        Precondition all = Stream.concat(fromMyStateData, Stream.concat(fromBlocks, fromPrevious))
                .map(pc -> pc == null ? Precondition.empty(sharedState.evaluationContext.getPrimitives()) : pc)
                .reduce((pc1, pc2) -> pc1.combine(sharedState.evaluationContext, pc2))
                .orElse(Precondition.empty(sharedState.evaluationContext.getPrimitives()));

        CausesOfDelay allDelayed = all.expression().causesOfDelay().merge(previousDelays).merge(subBlockDelays);

        if (allDelayed.isDelayed()) {
            combinedPrecondition.setVariable(all);
            return allDelayed;
        }

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
                // FIXME break immutable removed temporarily
                .map(vi -> vi.getLinkedVariables().causesOfDelay().merge(
                        vi.getProperty(Property.CONTEXT_MODIFIED).causesOfDelay()))
                .reduce(CausesOfDelay.EMPTY, CausesOfDelay::merge);
        if (delayed.isDelayed()) {
            linksHaveBeenEstablished.setVariable(delayed);
            return delayed;
        }
        linksHaveBeenEstablished.setFinal(delayed);
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
