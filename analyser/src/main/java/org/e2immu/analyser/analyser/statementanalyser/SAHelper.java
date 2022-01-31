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

import org.e2immu.analyser.analyser.Properties;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.util.AnalyserResult;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.config.DebugConfiguration;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.Filter;
import org.e2immu.analyser.model.expression.Instance;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.expression.util.LhsRhs;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.visitor.StatementAnalyserVariableVisitor;
import org.e2immu.analyser.visitor.StatementAnalyserVisitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.e2immu.analyser.analyser.Property.*;

record SAHelper(StatementAnalysis statementAnalysis) {
    private static final Logger LOGGER = LoggerFactory.getLogger(SAHelper.class);

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


        both.remove(IN_NOT_NULL_CONTEXT);
        handleInNotNullContext(previous, res);

        both.forEach(k -> {
            DV prev = previous.getOrDefault(k, k.valueWhenAbsent());
            DV change = changeData.getOrDefault(k, k.valueWhenAbsent());
            if (GroupPropertyValues.PROPERTIES.contains(k)) {
                DV value = switch (k) {
                    case EXTERNAL_CONTAINER -> prev.minIgnoreNotInvolved(change);
                    case CONTEXT_MODIFIED, EXTERNAL_IMMUTABLE, EXTERNAL_NOT_NULL -> prev.max(change);
                    case CONTEXT_IMMUTABLE -> evaluationContext.isMyself(variable) ? MultiLevel.MUTABLE_DV : prev.max(change);
                    case CONTEXT_CONTAINER -> evaluationContext.isMyself(variable) ? MultiLevel.NOT_CONTAINER_DV : prev.max(change);
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

    private static void handleInNotNullContext(Map<Property, DV> previous, Map<Property, DV> res) {
        DV prev = previous.getOrDefault(IN_NOT_NULL_CONTEXT, null);
        assert prev == null || prev.equals(DV.TRUE_DV);
        DV change = res.getOrDefault(IN_NOT_NULL_CONTEXT, null);
        if (change != null) {
            assert change.equals(DV.TRUE_DV);
            // leave things as they are
        } else {
            if (prev != null) res.put(IN_NOT_NULL_CONTEXT, prev);
        }
    }


    /*
    Variable is target of assignment. In terms of CNN/CM it should be neutral (rather than delayed), as its current value
    is not of relevance.

    There is no overlap between valueProps and variableProps
     */
    static Properties mergeAssignment(Variable variable,
                                      Properties valueProps,
                                      Properties changeData,
                                      GroupPropertyValues groupPropertyValues) {
        Properties res = valueProps.combine(changeData);

        // reasoning: only relevant when assigning to a field, this assignment is in StaticallyAssignedVars, so
        // the field's value is taken anyway
        groupPropertyValues.set(EXTERNAL_NOT_NULL, variable, EXTERNAL_NOT_NULL.valueWhenAbsent());
        groupPropertyValues.set(EXTERNAL_IMMUTABLE, variable, EXTERNAL_IMMUTABLE.valueWhenAbsent());
        groupPropertyValues.set(EXTERNAL_CONTAINER, variable, EXTERNAL_CONTAINER.valueWhenAbsent());

        DV cnn = res.remove(CONTEXT_NOT_NULL);
        groupPropertyValues.set(CONTEXT_NOT_NULL, variable, cnn == null ? AnalysisProvider.defaultNotNull(variable.parameterizedType()) : cnn);
        DV cm = res.remove(CONTEXT_MODIFIED);
        groupPropertyValues.set(CONTEXT_MODIFIED, variable, cm == null ? DV.FALSE_DV : cm);
        DV cImm = res.remove(CONTEXT_IMMUTABLE);
        groupPropertyValues.set(CONTEXT_IMMUTABLE, variable, cImm == null ? MultiLevel.MUTABLE_DV : cImm);
        DV cCont = res.remove(CONTEXT_CONTAINER);
        groupPropertyValues.set(CONTEXT_CONTAINER, variable, cCont == null ? MultiLevel.NOT_CONTAINER_DV : cCont);

        return res;
    }

    public void visitStatementVisitors(String statementId,
                                       AnalyserResult result,
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
            ConditionManager cm = statementAnalysis.stateData().getConditionManagerForNextStatement();
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

    public static EvaluationResult copyFromStateIntoValue(EvaluationResult initialResult,
                                                          EvaluationContext evaluationContext,
                                                          ConditionManager localConditionManager) {
        if (localConditionManager.stateIsDelayed().isDone() && !localConditionManager.state().isBooleanConstant()) {
            EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
            List<LhsRhs> equalities = LhsRhs.extractEqualities(localConditionManager.state());
            boolean changed = false;
            for (LhsRhs lhsRhs : equalities) {
                if (lhsRhs.rhs() instanceof VariableExpression ve && lhsRhs.lhs().isDone()) {
                    Expression currentValue = evaluationContext.currentValue(ve.variable());
                    if(currentValue instanceof Instance) {
                        LOGGER.debug("Caught equality on variable with 'instance' value {}: {}", ve.variable(), lhsRhs.lhs());
                        LinkedVariables linkedVariables = lhsRhs.lhs().linkedVariables(evaluationContext);
                        builder.assignment(ve.variable(), lhsRhs.lhs(), linkedVariables);
                        changed = true;
                    }
                }
            }
            if (changed) {
                return builder.compose(initialResult).build();
            }
        }
        return initialResult;
    }
}
