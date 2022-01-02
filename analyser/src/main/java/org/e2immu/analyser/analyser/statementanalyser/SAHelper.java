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

package org.e2immu.analyser.analyser.statementanalyser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.SimpleSet;
import org.e2immu.analyser.analyser.delay.VariableCause;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.Filter;
import org.e2immu.analyser.model.expression.InlineConditional;
import org.e2immu.analyser.model.variable.LocalVariableReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.model.variable.VariableNature;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.e2immu.analyser.analyser.Property.*;
import static org.e2immu.analyser.analyser.Property.CONTEXT_MODIFIED;
import static org.e2immu.analyser.analyser.VariableInfoContainer.Level.EVALUATION;

record SAHelper(StatementAnalysis statementAnalysis) {


    static Filter.FilterResult<ParameterInfo> moveConditionToParameter(EvaluationContext evaluationContext, Expression expression) {
        Filter filter = new Filter(evaluationContext, Filter.FilterMode.ACCEPT);
        Filter.FilterResult<ParameterInfo> result = filter.filter(expression, filter.individualNullOrNotNullClauseOnParameter());
        if (result != null && !result.accepted().isEmpty() && result.rest().isBoolValueTrue()) {
            return result;
        }
        return null;
    }

    static Expression bestValue(EvaluationResult.ChangeData valueChangeData, VariableInfo vi1) {
        if (valueChangeData != null && valueChangeData.value() != null) {
            return valueChangeData.value();
        }
        if (vi1 != null) {
            return vi1.getValue();
        }
        return null;
    }



    static Map<Property, DV> mergePreviousAndChange(
            EvaluationContext evaluationContext,
            Variable variable,
            Map<Property, DV> previous,
            Map<Property, DV> changeData,
            GroupPropertyValues groupPropertyValues,
            boolean allowValueProperties) {
        Set<Property> both = new HashSet<>(previous.keySet());
        both.addAll(changeData.keySet());
        both.addAll(GroupPropertyValues.PROPERTIES);
        Map<Property, DV> res = new HashMap<>(changeData);

        both.forEach(k -> {
            DV prev = previous.getOrDefault(k, k.valueWhenAbsent());
            DV change = changeData.getOrDefault(k, k.valueWhenAbsent());
            if (GroupPropertyValues.PROPERTIES.contains(k)) {
                DV value = switch (k) {
                    case EXTERNAL_IMMUTABLE, CONTEXT_MODIFIED, EXTERNAL_NOT_NULL -> prev.max(change);
                    case CONTEXT_IMMUTABLE -> evaluationContext.isMyself(variable) ? MultiLevel.MUTABLE_DV : prev.max(change);
                    case CONTEXT_NOT_NULL -> AnalysisProvider.defaultNotNull(variable.parameterizedType()).max(prev).max(change);
                    default -> throw new UnsupportedOperationException();
                };
                groupPropertyValues.set(k, variable, value);
            } else {
                switch (k) {
                    // value properties are copied from previous, only when the value from previous is copied as well
                    case NOT_NULL_EXPRESSION, CONTAINER, IMMUTABLE, IDENTITY, INDEPENDENT -> {
                        if (allowValueProperties) res.put(k, prev);
                    }
                    // all other properties are copied from change data
                    default -> res.put(k, change);
                }
            }
        });
        res.keySet().removeAll(GroupPropertyValues.PROPERTIES);
        return res;
    }


    /*
    Variable is target of assignment. In terms of CNN/CM it should be neutral (rather than delayed), as its current value
    is not of relevance.

    There is no overlap between valueProps and variableProps
     */
    static Map<Property, DV> mergeAssignment(Variable variable,
                                                     Map<Property, DV> valueProps,
                                                     Map<Property, DV> changeData,
                                                     GroupPropertyValues groupPropertyValues) {
        Map<Property, DV> res = new HashMap<>(valueProps);
        res.putAll(changeData);

        // reasoning: only relevant when assigning to a field, this assignment is in StaticallyAssignedVars, so
        // the field's value is taken anyway
        groupPropertyValues.set(EXTERNAL_NOT_NULL, variable, MultiLevel.NOT_INVOLVED_DV);
        groupPropertyValues.set(EXTERNAL_IMMUTABLE, variable, MultiLevel.NOT_INVOLVED_DV);

        DV cnn = res.remove(CONTEXT_NOT_NULL);
        groupPropertyValues.set(CONTEXT_NOT_NULL, variable, cnn == null ? AnalysisProvider.defaultNotNull(variable.parameterizedType()) : cnn);
        DV cm = res.remove(CONTEXT_MODIFIED);
        groupPropertyValues.set(CONTEXT_MODIFIED, variable, cm == null ? DV.FALSE_DV : cm);
        DV cImm = res.remove(CONTEXT_IMMUTABLE);
        groupPropertyValues.set(CONTEXT_IMMUTABLE, variable, cImm == null ? MultiLevel.MUTABLE_DV : cImm);

        return res;
    }

    static boolean assignmentToNonCopy(VariableInfoContainer vic, EvaluationResult evaluationResult) {
        if (vic.variableNature() instanceof VariableNature.CopyOfVariableInLoop) {
            Variable original = vic.variableNature().localCopyOf();
            EvaluationResult.ChangeData changeData = evaluationResult.changeData().get(original);
            return changeData != null && changeData.markAssignment();
        }
        return false;
    }

    public void visitStatementVisitors(String statementId,
                                       StatementAnalyserResult result,
                                       StatementAnalyserSharedState sharedState,
                                       DebugConfiguration debugConfiguration, AnalyserComponents<String, StatementAnalyserSharedState> analyserComponents) {
        MethodInfo methodInfo = statementAnalysis.methodAnalysis().getMethodInfo();
        for (StatementAnalyserVariableVisitor statementAnalyserVariableVisitor :
                debugConfiguration.statementAnalyserVariableVisitors()) {
            statementAnalysis.rawVariableStream()
                    .map(Map.Entry::getValue)
                    .forEach(variableInfoContainer -> statementAnalyserVariableVisitor.visit(
                            new StatementAnalyserVariableVisitor.Data(
                                    sharedState.evaluationContext().getIteration(),
                                    sharedState.evaluationContext(),
                                    methodInfo,
                                    statementId,
                                    variableInfoContainer.current().name(),
                                    variableInfoContainer.current().variable(),
                                    variableInfoContainer.current().getValue(),
                                    variableInfoContainer.current().getProperties(),
                                    variableInfoContainer.current(),
                                    variableInfoContainer)));
        }
        for (StatementAnalyserVisitor statementAnalyserVisitor : debugConfiguration.statementAnalyserVisitors()) {
            ConditionManager cm = statementAnalysis.stateData().conditionManagerForNextStatement.get();
            statementAnalyserVisitor.visit(
                    new StatementAnalyserVisitor.Data(
                            result,
                            sharedState.evaluationContext().getIteration(),
                            sharedState.evaluationContext(),
                            methodInfo,
                            statementAnalysis,
                            statementAnalysis.index(),
                            cm == null ? null : cm.condition(),
                            cm == null ? null : cm.state(),
                            cm == null ? null : cm.absoluteState(sharedState.evaluationContext()),
                            cm,
                            sharedState.localConditionManager(),
                            analyserComponents.getStatusesAsMap()));
        }
    }
}
