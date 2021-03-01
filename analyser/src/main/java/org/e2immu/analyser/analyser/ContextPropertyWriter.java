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

import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.util.DependencyGraph;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.LogTarget.LINKED_VARIABLES;
import static org.e2immu.analyser.util.Logger.log;

public class ContextPropertyWriter {

    private static final Variable DELAY_VAR = Variable.fake();

    private final DependencyGraph<Variable> dependencyGraph = new DependencyGraph<>();

    /**
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
                                Set<Variable> doNotWrite) {
        final AtomicReference<AnalysisStatus> analysisStatus = new AtomicReference<>(DONE);

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
                            if (!doNotWrite.contains(linkedVariable)) {
                                assignToLinkedVariable(statementAnalysis, progress, summary, linkedVariable,
                                        variableProperty, level, valuesToSet);
                            }
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
            Integer v = values.get(variable);
            if (v == null) {
                throw new NullPointerException("Expect " + variable.fullyQualifiedName() + " to be known for "
                        + variableProperty + ", map is " + values);
            }
            if (v == Level.DELAY) hasDelays = true;
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
                valuesToSet.put(vic, Level.DELAY);
            }
        } else if (current < newValue && newValue != Level.DELAY) {
            throw new UnsupportedOperationException("? already have " + current + ", computed "
                    + newValue + " variable " + vi.variable().fullyQualifiedName() + ", prop " + variableProperty);
        } /* else: it is possible that the previous value was higher: statements at the end of the block
        can become unreachable, which may lower the context value; see Loops_7
        */
    }

}
