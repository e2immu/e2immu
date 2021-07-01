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

import org.e2immu.analyser.analyser.util.DelayDebugger;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.util.DependencyGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;

public class ContextPropertyWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(ContextPropertyWriter.class);
    public static final String CONTEXT_PROPERTY_WRITER = "ContextPropertyWriter";

    private final DependencyGraph<AugmentedVariable> dependencyGraph = new DependencyGraph<>();

    record LocalCopy(LocalVariableReference localVariableReference, int index) {
    }

    /*
    map$0 may be linked to the field map
    map$1 may be linked as well.
    Without breaking the link between map$0 and map, we'd have map$0 and map$1 in the same cluster,
    which causes problems because where map$0 may be non-null in a condition (but not CNN), map$1 may be only
    not null CNN.
     */
    record LocalCopyData(Map<FieldReference, List<LocalCopy>> map) {
        public static final LocalCopyData EMPTY = new LocalCopyData(Map.of());

        boolean accept(Variable v1, Variable v2) {
            if (v1 instanceof FieldReference fieldReference && v2 instanceof LocalVariableReference lvr) {
                return accept(fieldReference, lvr);
            }
            if (v2 instanceof FieldReference fieldReference && v1 instanceof LocalVariableReference lvr) {
                return accept(fieldReference, lvr);
            }
            return true;
        }

        boolean accept(FieldReference fieldReference, LocalVariableReference lvr) {
            if (lvr.variable.nature() instanceof VariableNature.CopyOfVariableField) {
                List<LocalCopy> list = map.get(fieldReference);
                if (list != null) {
                    return lvr.equals(list.get(0).localVariableReference);
                }
            }
            return true;
        }
    }

    public static LocalCopyData localCopyPreferences(Collection<Variable> variables) {
        Map<FieldReference, List<LocalCopy>> map = new HashMap<>();
        for (Variable variable : variables) {
            if (variable instanceof LocalVariableReference lvr
                    && lvr.variable.nature() instanceof VariableNature.CopyOfVariableField copy) {
                List<LocalCopy> list = map.computeIfAbsent(copy.localCopyOf(), k -> new ArrayList<>());
                LocalCopy localCopy = new LocalCopy(lvr, copy.statementTime());
                if (!list.contains(localCopy)) list.add(localCopy);
            }
        }
        map.values().forEach(list -> list.sort(Comparator.comparingInt(lvr -> -lvr.index)));
        return new LocalCopyData(map);
    }

    record AugmentedVariable(Variable variable, Boolean delayed, Boolean breakImmutable) {

        public AugmentedVariable(Variable v) {
            this(v, null, null);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            AugmentedVariable that = (AugmentedVariable) o;
            return variable.equals(that.variable);
        }

        @Override
        public int hashCode() {
            return Objects.hash(variable);
        }
    }

    private AnalysisStatus fillDependencyGraph(StatementAnalysis statementAnalysis,
                                               EvaluationContext evaluationContext,
                                               Function<VariableInfo, LinkedVariables> connections,
                                               VariableInfoContainer.Level level,
                                               String variablePropertyNameForDebugging,
                                               LocalCopyData localCopyData,
                                               AtomicBoolean onlyDoSetsWithBreak) {
        // delays in dependency graph
        AtomicReference<AnalysisStatus> analysisStatus = new AtomicReference<>(DONE);
        statementAnalysis.variableStream(level)
                .filter(VariableInfo::isNotConditionalInitialization)
                .forEach(variableInfo -> {
                    LinkedVariables linkedVariables = connections.apply(variableInfo);
                    boolean externalImmutableBreak;
                    if (onlyDoSetsWithBreak != null) {
                        externalImmutableBreak = variableInfo.getProperty(VariableProperty.EXTERNAL_IMMUTABLE_BREAK_DELAY) == Level.TRUE;
                        if (externalImmutableBreak) onlyDoSetsWithBreak.set(true);
                    } else {
                        externalImmutableBreak = false;
                    }
                    if (linkedVariables.isDelayed() && !externalImmutableBreak) {
                        if (!(variableInfo.variable() instanceof LocalVariableReference) || variableInfo.isAssigned()) {
                            log(DELAYED, "Delaying MethodLevelData for {} in {}: linked variables not set",
                                    variableInfo.variable().fullyQualifiedName(), evaluationContext.getLocation());

                            assert statementAnalysis.translatedDelay(CONTEXT_PROPERTY_WRITER,
                                    variableInfo.variable().fullyQualifiedName() + "@" + statementAnalysis.index + DelayDebugger.D_LINKED_VARIABLES_SET,
                                    statementAnalysis.fullyQualifiedName() + "." + variablePropertyNameForDebugging);
                            analysisStatus.set(DELAYS);
                        }
                    }

                    AugmentedVariable from = new AugmentedVariable(variableInfo.variable(),
                            linkedVariables.isDelayed(), externalImmutableBreak);
                    List<AugmentedVariable> to = linkedVariables.variables().stream()
                            .filter(t -> localCopyData.accept(from.variable, t))
                            .filter(t -> statementAnalysis.variables.isSet(t.fullyQualifiedName()))
                            .map(AugmentedVariable::new)
                            .toList();
                    dependencyGraph.addNode(from, to, true);
                });
        return analysisStatus.get();
    }

    /**
     * Assign a single summarized value for each linked variable group.
     * <p>
     * No delay -> assign all values
     * Delay + No break immutable -> do not assign values
     * Delay + Break immutable -> does the delay occur in the break groups only?
     * YES: assign all
     * NO: only break groups (in partial mode)
     *
     * @param statementAnalysis the statement
     * @param evaluationContext the eval context, used for creating EVAL level if needed
     * @param connections       either getLinkedVariables, or getStaticallyAssignedVariables
     * @return status
     */

    public AnalysisStatus write(StatementAnalysis statementAnalysis,
                                EvaluationContext evaluationContext,
                                Function<VariableInfo, LinkedVariables> connections,
                                VariableProperty variableProperty,
                                Map<Variable, Integer> propertyValues,
                                VariableInfoContainer.Level level,
                                Set<Variable> doNotWrite,
                                LocalCopyData localCopyData) {
        final AtomicBoolean onlyDoSetsWithBreak = variableProperty == VariableProperty.CONTEXT_MODIFIED ? new AtomicBoolean() : null;

        AnalysisStatus graphStatus = fillDependencyGraph(statementAnalysis, evaluationContext, connections, level,
                variableProperty.name(), localCopyData, onlyDoSetsWithBreak);

        if (graphStatus == DELAYS && (onlyDoSetsWithBreak == null || !onlyDoSetsWithBreak.get()))
            return graphStatus;

        final AtomicBoolean progress = new AtomicBoolean();
        final boolean inPartialMode = graphStatus == DELAYS && delayOutsideBreakGroups();

        // we make a copy of the values, because in summarizeModification there is the possibility of adding to the map
        Map<VariableInfoContainer, Integer> valuesToSet = new HashMap<>();

        AtomicReference<AnalysisStatus> summaryStatus = new AtomicReference<>(DONE);
        dependencyGraph.visitBidirectionalGroups(list -> {
            int summary = summarizeContext(statementAnalysis, list, variableProperty, propertyValues);
            if (summary == Level.DELAY) summaryStatus.set(DELAYS);

            if (inPartialMode) {
                boolean haveBreakInSet = list.stream().anyMatch(av -> av.breakImmutable != null && av.breakImmutable);
                if (!haveBreakInSet) return;
            }
            // this loop is critical, see Container_3, do not remove it again :-)
            try {
                for (AugmentedVariable av : list) {
                    if (!doNotWrite.contains(av.variable)) {
                        assignToLinkedVariable(statementAnalysis, progress, summary, av.variable,
                                variableProperty, level, valuesToSet);
                    }
                }
            } catch (RuntimeException re) {
                LOGGER.error("Summary {}: {}, vars: {}", variableProperty, summary, list);
                throw re;
            }
        });
        valuesToSet.forEach((k, v) -> {
            if (v != Level.DELAY) {
                k.setProperty(variableProperty, v, level);
            }
        });
        return graphStatus.combine(summaryStatus.get()) == DELAYS ? (progress.get() ? PROGRESS : DELAYS) : DONE;
    }

    private boolean delayOutsideBreakGroups() {
        AtomicBoolean delayOutsideBreakGroup = new AtomicBoolean();
        dependencyGraph.visitBidirectionalGroups(list -> {
            boolean delay = list.stream().anyMatch(av -> av.delayed != null && av.delayed);
            boolean breakGroup = list.stream().anyMatch(av -> av.breakImmutable != null && av.breakImmutable);
            if (delay && !breakGroup) delayOutsideBreakGroup.set(true);
        });
        return delayOutsideBreakGroup.get();
    }

    private static int summarizeContext(StatementAnalysis statementAnalysis,
                                        List<AugmentedVariable> linkedVariables,
                                        VariableProperty variableProperty,
                                        Map<Variable, Integer> values) {
        boolean hasDelays = false;
        int max = Level.DELAY;
        for (AugmentedVariable av : linkedVariables) {
            Integer v = values.get(av.variable);
            if (v == null) {
                throw new NullPointerException("Expect " + av.variable.fullyQualifiedName() + " to be known for "
                        + variableProperty + ", map is " + values);
            }
            if (v == Level.DELAY) {
                assert statementAnalysis.translatedDelay(CONTEXT_PROPERTY_WRITER,
                        av.variable.fullyQualifiedName() + "@" + statementAnalysis.index + "." + variableProperty.name(),
                        statementAnalysis.fullyQualifiedName() + "." + variableProperty.name());

                hasDelays = true;
            }
            max = Math.max(max, v);
        }
        return hasDelays && max < variableProperty.best ? Level.DELAY : max;
    }

    private static void assignToLinkedVariable(StatementAnalysis statementAnalysis,
                                               AtomicBoolean progress,
                                               int newValue,
                                               Variable linkedVariable,
                                               VariableProperty variableProperty,
                                               VariableInfoContainer.Level level,
                                               Map<VariableInfoContainer, Integer> valuesToSet) {
        VariableInfoContainer vic = statementAnalysis.variables.get(linkedVariable.fullyQualifiedName());
        if (level.equals(VariableInfoContainer.Level.EVALUATION)) {
            if (!vic.hasMerge() && !vic.hasEvaluation()) {
                vic.prepareEvaluationForWritingContextProperties();
            }
        } else if (!vic.hasMerge()) {
            vic.prepareMergeForWritingContextProperties();
        }

        VariableInfo vi = vic.best(level);
        int current = vi.getProperty(variableProperty);
        if (current == Level.DELAY) {
            // break the delay in case the variable is not even read

            if (newValue != Level.DELAY) {
                // once delay, always delay
                valuesToSet.merge(vic, newValue, (v1, v2) -> v1 == Level.DELAY ? Level.DELAY : Math.max(v1, v2));
                progress.set(true);
            } else {
                assert statementAnalysis.foundDelay(CONTEXT_PROPERTY_WRITER,
                        vi.variable().fullyQualifiedName() + "@" + statementAnalysis.index + "." + variableProperty.name());
                valuesToSet.put(vic, Level.DELAY);
            }
        }
    }
    // allow; the reason are conflicting values on different local copies, both linking to this
    // confirmed variable field. (see TrieSimplified_0, among others.) They do not matter, as
    // the context values for MethodAnalyser and FieldAnalyser is taken over ALL the local copies as well.

            /*
            yet another situation where overwriting seems to make no sense: in the first iteration, when
            variables in loop but defined outside still use the original variable, conflicts may arise (TrieSimplified_5)
             */
    //      throw new UnsupportedOperationException("? already have " + current + ", computed "
    //             + newValue + " variable " + vi.variable().fullyQualifiedName() + ", prop " + variableProperty);
         /* else: it is possible that the previous value was higher: statements at the end of the block
        can become unreachable, which may lower the context value; see Loops_7
        */


}
