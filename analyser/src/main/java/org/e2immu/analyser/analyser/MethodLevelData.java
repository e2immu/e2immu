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

import org.e2immu.analyser.model.Level;
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

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.DELAYS;
import static org.e2immu.analyser.analyser.AnalysisStatus.DONE;
import static org.e2immu.analyser.util.EventuallyFinalExtension.setFinalAllowEquals;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;

/**
 * IMPORTANT:
 * Method level data is incrementally copied from one statement to the next.
 * The method analyser will only investigate the data from the last statement in the method!
 */
public class MethodLevelData {
    private static final Logger LOGGER = LoggerFactory.getLogger(MethodLevelData.class);

    // part of modification status for dealing with circular methods
    private final SetOnce<Boolean> callsPotentiallyCircularMethod = new SetOnce<>();

    public Boolean getCallsPotentiallyCircularMethod() {
        return callsPotentiallyCircularMethod.getOrDefaultNull();
    }

    public final SetOnceMap<MethodInfo, Boolean> copyModificationStatusFrom = new SetOnceMap<>();

    // aggregates the preconditions on individual statements
    public final EventuallyFinal<Precondition> combinedPrecondition = new EventuallyFinal<>();

    // not for local processing, but so that we know in the method and field analyser that this process has been completed
    public final FlipSwitch linksHaveBeenEstablished = new FlipSwitch();

    private final EventuallyFinal<Set<WithInspectionAndAnalysis>> causesOfContextModificationDelay = new EventuallyFinal<>();

    public void addCircularCall() {
        if (!callsPotentiallyCircularMethod.isSet()) {
            callsPotentiallyCircularMethod.set(true);
        }
    }

    public Set<Variable> combinedPreconditionIsDelayedSet() {
        if (combinedPrecondition.isFinal()) return null;
        Precondition cp = combinedPrecondition.get();
        if (cp == null) return Set.of();
        return combinedPrecondition.get().expression().variables().stream().collect(Collectors.toUnmodifiableSet());
    }

    public boolean causesOfContextModificationDelayIsVariable() {
        return causesOfContextModificationDelay.isVariable();
    }

    public void causesOfContextModificationDelayAddVariable(Set<WithInspectionAndAnalysis> set) {
        assert causesOfContextModificationDelay.isVariable();
        if (causesOfContextModificationDelay.get() == null) {
            causesOfContextModificationDelay.setVariable(new HashSet<>());
        }
        causesOfContextModificationDelay.get().addAll(set);
    }

    public void causesOfContextModificationDelaySetFinal() {
        causesOfContextModificationDelay.setFinal(Set.of());
    }

    public Set<WithInspectionAndAnalysis> getCausesOfContextModificationDelay() {
        return causesOfContextModificationDelay.get();
    }

    public boolean acceptLinksHaveBeenEstablished(Predicate<WithInspectionAndAnalysis> canBeIgnored) {
        if (linksHaveBeenEstablished.isSet()) return true;
        Set<WithInspectionAndAnalysis> causes = causesOfContextModificationDelay.get();
        if (causes != null && !causes.isEmpty() && causes.stream().allMatch(canBeIgnored)) {
            log(ANALYSER, "Accepting a limited version of linksHaveBeenEstablished to break delay cycle");
            return true;
        }
        return false;
    }

    record SharedState(StatementAnalyserResult.Builder builder,
                       EvaluationContext evaluationContext,
                       StatementAnalysis statementAnalysis,
                       String logLocation,
                       MethodLevelData previous,
                       StateData stateData) {
    }

    public final AnalyserComponents<String, SharedState> analyserComponents = new AnalyserComponents.Builder<String, SharedState>()
            .add("mergeCausesOfContextModificationDelay", this::mergeCausesOfContextModificationDelay)
            .add("ensureThisProperties", sharedState -> ensureThisProperties())
            .add("linksHaveBeenEstablished", this::linksHaveBeenEstablished)
            .add("combinePrecondition", this::combinePrecondition)
            .build();

    private AnalysisStatus mergeCausesOfContextModificationDelay(SharedState sharedState) {
        if (causesOfContextModificationDelay.isFinal()) return DONE;
        if (sharedState.previous != null && sharedState.previous.causesOfContextModificationDelay.isVariable()) {
            if (causesOfContextModificationDelay.get() == null) {
                causesOfContextModificationDelay.setVariable(new HashSet<>());
            }
            causesOfContextModificationDelay.get().addAll(sharedState.previous.causesOfContextModificationDelay.get());
        }
        sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks().stream()
                .filter(sa -> sa.methodLevelData.causesOfContextModificationDelay.get() != null)
                .flatMap(sa -> sa.methodLevelData.causesOfContextModificationDelay.get().stream())
                .forEach(set -> {
                    if (set != null) causesOfContextModificationDelay.get().add(set);
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
        boolean delays = sharedState.previous != null && sharedState.previous.combinedPrecondition.isVariable();

        List<StatementAnalysis> subBlocks = sharedState.statementAnalysis.lastStatementsOfNonEmptySubBlocks();
        delays |= subBlocks.stream().anyMatch(sa -> sa.methodLevelData.combinedPrecondition.isVariable());
        delays |= !sharedState.stateData.preconditionIsFinal();

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

        delays |= sharedState.evaluationContext.isDelayed(all.expression());
        if (delays) combinedPrecondition.setVariable(all);
        else setFinalAllowEquals(combinedPrecondition, all);

        return delays ? DELAYS : DONE;
    }

    private AnalysisStatus linksHaveBeenEstablished(SharedState sharedState) {
        assert !linksHaveBeenEstablished.isSet();

        Optional<VariableInfo> delayed = sharedState.statementAnalysis.variableStream()
                .filter(vi -> !(vi.variable() instanceof This))
                // local variables that have been created, but not yet assigned/read
                .filter(vi -> !(vi.variable() instanceof LocalVariableReference)
                        || vi.isAssigned() || vi.isRead())
                .filter(vi -> !vi.linkedVariablesIsSet()
                        || vi.getProperty(VariableProperty.CONTEXT_MODIFIED) == Level.DELAY)
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
        if (!callsPotentiallyCircularMethod.isSet()) {
            callsPotentiallyCircularMethod.set(false);
        }
        return DONE;
    }
}
