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
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.config.AnalyserProgram;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.support.EventuallyFinal;
import org.e2immu.support.SetOnceMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
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

    public static final String LINKS_HAVE_BEEN_ESTABLISHED = "linksHaveBeenEstablished";
    public static final String COMBINE_PRECONDITION = "combinePrecondition";

    public final SetOnceMap<MethodInfo, Boolean> copyModificationStatusFrom = new SetOnceMap<>();

    // aggregates the preconditions on individual statements
    private final EventuallyFinal<Precondition> combinedPrecondition = new EventuallyFinal<>();

    // not for local processing, but so that we know in the method and field analyser that this process has been completed
    private final EventuallyFinal<CausesOfDelay> linksHaveBeenEstablished = new EventuallyFinal<>();

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

    public boolean combinedPreconditionIsFinal() {
        return combinedPrecondition.isFinal();
    }

    public Precondition combinedPreconditionGet() {
        return combinedPrecondition.get();
    }

    public void internalAllDoneCheck() {
        assert combinedPrecondition.isFinal();
        assert linksHaveBeenEstablished.isFinal();
    }

    public void makeUnreachable(Primitives primitives) {
        if (combinedPrecondition.isVariable()) combinedPrecondition.setFinal(Precondition.empty(primitives));
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
            new AnalyserComponents.Builder<String, SharedState>(AnalyserProgram.PROGRAM_ALL)
                    .add(LINKS_HAVE_BEEN_ESTABLISHED, this::linksHaveBeenEstablished)
                    .add(COMBINE_PRECONDITION, this::combinePrecondition)
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
            LOGGER.warn("Caught exception in linking computation, {}", logLocation);
            throw rte;
        }
    }


    // preconditions come from the precondition expression in stateData
    // they are accumulated from the previous statement, and from all child statements
    private AnalysisStatus combinePrecondition(SharedState sharedState) {
        Stream<Precondition> fromMyStateData = Stream.of(sharedState.stateData.getPrecondition());

        Stream<Precondition> fromPrevious = sharedState.previous != null ?
                Stream.of(sharedState.previous.combinedPrecondition.get()) : Stream.of();

        List<StatementAnalysis> subBlocks = sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks();
        Stream<Precondition> fromBlocks = subBlocks.stream()
                .map(sa -> sa.methodLevelData().combinedPrecondition)
                .map(EventuallyFinal::get);

        Precondition empty = Precondition.empty(sharedState.context.getPrimitives());
        Precondition all = Stream.concat(fromMyStateData, Stream.concat(fromBlocks, fromPrevious))
                .map(pc -> pc == null ? empty : pc)
                .reduce((pc1, pc2) -> pc1.combine(sharedState.context, pc2))
                .orElse(empty);

        if (all.isDelayed()) {
            combinedPrecondition.setVariable(all);
            return all.causesOfDelay();
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
                .map(vi -> vi.getLinkedVariables().causesOfDelay().merge(
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
}
