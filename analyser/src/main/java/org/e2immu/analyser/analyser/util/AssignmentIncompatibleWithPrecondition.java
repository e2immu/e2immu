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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.StatementAnalysis;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.ConstantExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.e2immu.analyser.util.Logger.LogTarget.EVENTUALLY;
import static org.e2immu.analyser.util.Logger.log;

public class AssignmentIncompatibleWithPrecondition {
    /**
     * @return null indicates delay; true indicates @Mark; also becomes @Only(before=)
     *
     * <p>
     * Possible situations:
     * <ul>
     * <li>recondition does null check
     * <li>precondition is of boolean nature
     * <li>precondition is of integer nature, compares with Equals or GreaterThanZero
     * <li>precondition is cause by method call to other internal @Mark/@Only method
     * </ul>
     */
    public static DV isMark(AnalyserContext analyserContext,
                            Precondition precondition,
                            MethodAnalyser methodAnalyser) {
        Set<Variable> variables = new HashSet<>(precondition.expression().variables(true));
        for (Variable variable : variables) {
            FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;

            for (VariableInfo variableInfo : methodAnalyser.getFieldAsVariable(fieldInfo)) {
                boolean assigned = variableInfo.isAssigned();
                if (assigned) {
                    Expression pcExpression = precondition.expression();
                    String index = variableInfo.getAssignmentIds().getLatestAssignmentIndex();
                    log(EVENTUALLY, "Field {} is assigned in {}, {}", variable.fullyQualifiedName(),
                            methodAnalyser.getMethodInfo().distinguishingName(), index);

                    StatementAnalyser statementAnalyser = methodAnalyser.findStatementAnalyser(index);
                    StatementAnalysis statementAnalysis = statementAnalyser.getStatementAnalysis();
                    EvaluationContext evaluationContext = statementAnalyser.newEvaluationContextForOutside();

                    VariableExpression ve;
                    if (fieldInfo.type.isNumeric()) {
                        Expression value = variableInfo.getValue();
                        if (value instanceof ConstantExpression) {
                            Boolean incompatible = remapReturnIncompatible(evaluationContext, variable,
                                    variableInfo.getValue(), pcExpression);
                            if (incompatible != null) return DV.fromBoolDv(incompatible);
                        } else if ((ve = value.asInstanceOf(VariableExpression.class)) != null) {
                            // grab some state about this variable
                            Expression state = statementAnalysis.stateData().conditionManagerForNextStatement.get()
                                    .individualStateInfo(evaluationContext, ve.variable());
                            if (!state.isBoolValueTrue()) {
                                Map<Expression, Expression> map = Map.of(new VariableExpression(ve.variable()), new VariableExpression(variable));
                                EvaluationContext neutralEc = new ConditionManager.EvaluationContextImpl(analyserContext);
                                Expression stateInTermsOfField = state.reEvaluate(neutralEc, map).getExpression();
                                return DV.fromBoolDv(!isCompatible(evaluationContext, stateInTermsOfField, pcExpression));
                            }
                        }
                    } else if (fieldInfo.type.isBoolean()) {
                        Boolean incompatible = remapReturnIncompatible(evaluationContext, variable,
                                variableInfo.getValue(), pcExpression);
                        if (incompatible != null) return DV.fromBoolDv(incompatible);
                    } else {
                        // normal object null checking for now
                        Expression notNull = statementAnalysis.notNullValuesAsExpression(evaluationContext);
                        Expression state = statementAnalysis.stateData().conditionManagerForNextStatement.get().state();
                        Expression combined = And.and(evaluationContext, state, notNull);

                        if (isCompatible(evaluationContext, combined, pcExpression)) {
                            CausesOfDelay delays = statementAnalysis.stateData().conditionManagerForNextStatementStatus();
                            if (delays.isDelayed()) {
                                return delays; // IMPROVE we're not gathering them, rather returning the first one here
                            }
                            return DV.FALSE_DV;
                        }
                        return DV.TRUE_DV;
                    }
                }
            }
        }

        // METHOD
        MethodAnalysis.Eventual consistent = null;
        CausesOfDelay causesOfDelay = CausesOfDelay.EMPTY;
        for (Precondition.PreconditionCause preconditionCause : precondition.causes()) {
            // example in FlipSwitch, where copy() calls set(), which should have been handled before
            if (preconditionCause instanceof Precondition.MethodCallCause methodCallCause) {
                MethodInfo calledMethod = methodCallCause.methodInfo();
                MethodAnalysis calledMethodAnalysis = analyserContext.getMethodAnalysis(calledMethod);
                CausesOfDelay delays = calledMethodAnalysis.eventualStatus();
                if (delays.isDone()) {
                    MethodAnalysis.Eventual eventual = calledMethodAnalysis.getEventual();
                    if (eventual != MethodAnalysis.NOT_EVENTUAL) {
                        if (consistent == null) consistent = eventual;
                        else if (!eventual.consistentWith(consistent)) {
                            consistent = null;
                            break;
                        }
                    }
                } else {
                    causesOfDelay = causesOfDelay.merge(delays);
                }
            }
        }
        if (causesOfDelay.isDelayed()) return causesOfDelay;
        if (consistent != null) {
            return DV.fromBoolDv(consistent.mark());
        }
        return DV.FALSE_DV;
    }


    private static Boolean remapReturnIncompatible(EvaluationContext evaluationContext,
                                                   Variable variable,
                                                   Expression value,
                                                   Expression precondition) {
        Map<Expression, Expression> map = Map.of(new VariableExpression(variable), value);
        Expression reEvaluated = precondition.reEvaluate(evaluationContext, map).getExpression();
        // false ~ incompatible with precondition
        if (reEvaluated.isBooleanConstant()) return reEvaluated.isBoolValueFalse();
        return null;
    }

    private static boolean isCompatible(EvaluationContext evaluationContext, Expression v1, Expression v2) {
        Expression and = And.and(evaluationContext, v1, v2);
        return v1.equals(and) || v2.equals(and);
    }
}
