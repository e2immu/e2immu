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
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.DelayedVariableExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.util.DependencyGraph;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.e2immu.analyser.analyser.AnalysisStatus.*;
import static org.e2immu.analyser.analyser.VariableInfoContainer.Level.EVALUATION;
import static org.e2immu.analyser.analyser.VariableProperty.CONTEXT_DEPENDENT;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.log;

/*
deals with 2 situations:
1- linked1Variables in ChangeData contain scope, holding variable must be linked to parameter
2- a variable which has a TEMP_DEPENDENT1 property, becomes dependent1 IF linked to field
 */
public class Linked1Writer {
    private static final String LINKED1_WRITER = "Linked1Writer";
    private final AtomicReference<AnalysisStatus> analysisStatus = new AtomicReference<>(DONE);
    private final StatementAnalysis statementAnalysis;
    private final DependencyGraph<Variable> dependencyGraph = new DependencyGraph<>();

    public Linked1Writer(StatementAnalysis statementAnalysis,
                         EvaluationContext evaluationContext,
                         Function<VariableInfo, LinkedVariables> connections,
                         ContextPropertyWriter.LocalCopyData localCopyData) {
        this.statementAnalysis = statementAnalysis;
        fillDependencyGraph(statementAnalysis, evaluationContext,
                connections, localCopyData);
    }

    public AnalysisStatus write(Map<Variable, EvaluationResult.ChangeData> changeDataMap) {
        final AtomicBoolean progress = new AtomicBoolean();
        if (analysisStatus.get() == DELAYS) return analysisStatus.get();

        statementAnalysis.variables.stream().map(Map.Entry::getValue).forEach(vic -> {
            VariableInfo best = vic.best(EVALUATION);
            handleLinked1Variables(vic, best, changeDataMap.get(best.variable()), progress);
        });

        return analysisStatus.get() == DELAYS ? (progress.get() ? PROGRESS : DELAYS) : DONE;
    }

    private void fillDependencyGraph(StatementAnalysis statementAnalysis,
                                     EvaluationContext evaluationContext,
                                     Function<VariableInfo, LinkedVariables> connections,
                                     ContextPropertyWriter.LocalCopyData localCopyData) {
        // delays in dependency graph
        statementAnalysis.variableStream(EVALUATION)
                .filter(VariableInfo::isNotConditionalInitialization)
                .forEach(variableInfo -> {
                    LinkedVariables linkedVariables = connections.apply(variableInfo);
                    boolean ignoreDelay = variableInfo.getProperty(VariableProperty.EXTERNAL_IMMUTABLE_BREAK_DELAY) == Level.TRUE;
                    if (linkedVariables.isDelayed() && !ignoreDelay) {
                        if (!(variableInfo.variable() instanceof LocalVariableReference) || variableInfo.isAssigned()) {
                            log(DELAYED, "Delaying MethodLevelData for {} in {}: linked1 variables not set",
                                    variableInfo.variable().fullyQualifiedName(), evaluationContext.getLocation());

                            assert statementAnalysis.translatedDelay(LINKED1_WRITER,
                                    variableInfo.variable().fullyQualifiedName() + "@" + statementAnalysis.index + DelayDebugger.D_LINKED_VARIABLES_SET,
                                    statementAnalysis.fullyQualifiedName() + ".LINKED1");
                            analysisStatus.set(DELAYS);
                        }
                    } else {
                        Variable from = variableInfo.variable();
                        List<Variable> to = linkedVariables.variables().stream()
                                .filter(t -> localCopyData.accept(from, t)).toList();
                        dependencyGraph.addNode(from, to, true);
                    }
                });
    }

    private void handleLinked1Variables(VariableInfoContainer vic,
                                        VariableInfo best,
                                        EvaluationResult.ChangeData changeData,
                                        AtomicBoolean progress) {
        Variable inArgument = best.variable();

        Set<Variable> dependenciesOfArgument = dependencyGraph.dependencies(inArgument);

        Set<Variable> paramsInArgument = dependenciesOfArgument.stream()
                .filter(v -> v instanceof ParameterInfo).collect(Collectors.toSet());
        boolean setProperty = false;

        if (!paramsInArgument.isEmpty() && changeData != null) {
            LinkedVariables inScope = changeData.linked1Variables();
            for (Variable varInScope : inScope.variables()) {
                Set<Variable> varsInScope = dependencyGraph.dependencies(varInScope);

                Set<Variable> fieldsInScope = varsInScope.stream()
                        .filter(v -> v instanceof This || v instanceof FieldReference fr && fr.scopeIsThis())
                        .collect(Collectors.toSet());
                if (!fieldsInScope.isEmpty()) {
                    // we have a hit!
                    setProperty = true;
                    vic.setProperty(CONTEXT_DEPENDENT, MultiLevel.DEPENDENT_1, EVALUATION);
                }
            }
        }

        if (setProperty) {
            progress.set(true);
        } else if (inArgument instanceof ParameterInfo && vic.hasEvaluation() &&
                best.getProperty(CONTEXT_DEPENDENT) == Level.DELAY) {
            vic.setProperty(CONTEXT_DEPENDENT, MultiLevel.DEPENDENT, EVALUATION);
            progress.set(true);
        }
    }

    public Boolean isLinkedToField(Expression expression) {
        if (expression instanceof DelayedVariableExpression) return null;
        VariableExpression ve;
        if ((ve = expression.asInstanceOf(VariableExpression.class)) != null) {
            Set<Variable> set = dependencyGraph.dependencies(ve.variable());
            return set.stream().anyMatch(v -> v instanceof This || v instanceof FieldReference fr && fr.scopeIsThis());
        }
        return false;
    }
}
