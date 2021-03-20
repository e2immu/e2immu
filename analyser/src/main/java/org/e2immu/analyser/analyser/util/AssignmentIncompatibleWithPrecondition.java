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
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.FieldInfo;
import org.e2immu.analyser.model.MethodAnalysis;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.expression.And;
import org.e2immu.analyser.model.expression.ConstantExpression;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.statement.Block;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Primitives;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.e2immu.analyser.util.Logger.LogTarget.MARK;
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
    public static Boolean isMark(AnalyserContext analyserContext,
                                 Expression precondition,
                                 MethodAnalyser methodAnalyser,
                                 boolean methods) {
        Set<Variable> variables = new HashSet<>(precondition.variables());
        for (Variable variable : variables) {
            FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;

            for (VariableInfo variableInfo : methodAnalyser.getFieldAsVariable(fieldInfo, false)) {
                boolean assigned = variableInfo.isAssigned();
                if (assigned) {

                    String index = VariableInfoContainer.statementId(variableInfo.getAssignmentId());
                    log(MARK, "Field {} is assigned in {}, {}", variable.fullyQualifiedName(),
                            methodAnalyser.methodInfo.distinguishingName(), index);

                    StatementAnalyser statementAnalyser = methodAnalyser.findStatementAnalyser(index);
                    StatementAnalysis statementAnalysis = statementAnalyser.statementAnalysis;
                    EvaluationContext evaluationContext = statementAnalyser.newEvaluationContextForOutside();


                    if (Primitives.isNumeric(fieldInfo.type)) {
                        Expression value = variableInfo.getValue();
                        if (value instanceof ConstantExpression) {
                            Boolean incompatible = remapReturnIncompatible(evaluationContext, variable,
                                    variableInfo.getValue(), precondition);
                            if (incompatible != null) return incompatible;
                        } else if (value instanceof VariableExpression ve) {
                            // grab some state about this variable
                            Expression state = statementAnalysis.stateData.conditionManagerForNextStatement.get()
                                    .individualStateInfo(evaluationContext, ve.variable());
                            if (!state.isBoolValueTrue()) {
                                Map<Expression, Expression> map = Map.of(new VariableExpression(ve.variable()), new VariableExpression(variable));
                                EvaluationContext neutralEc = new ConditionManager.EvaluationContextImpl(analyserContext);
                                Expression stateInTermsOfField = state.reEvaluate(neutralEc, map).getExpression();
                                return !isCompatible(evaluationContext, stateInTermsOfField, precondition);
                            }
                        }
                    } else if (Primitives.isBoolean(fieldInfo.type)) {
                        Boolean incompatible = remapReturnIncompatible(evaluationContext, variable,
                                variableInfo.getValue(), precondition);
                        if (incompatible != null) return incompatible;
                    } else {
                        // normal object null checking for now
                        Expression notNull = statementAnalysis.notNullValuesAsExpression(evaluationContext);
                        Expression state = statementAnalysis.stateData.conditionManagerForNextStatement.get().state();
                        Expression combined = new And(evaluationContext.getPrimitives()).append(evaluationContext, state, notNull);

                        if (isCompatible(evaluationContext, combined, precondition)) {
                            if (statementAnalysis.stateData.conditionManagerForNextStatement.isVariable()) {
                                return null; // DELAYS
                            }
                            return false;
                        }
                        return true;
                    }
                }
            }
        }

        if (!methods) return false;

        // METHOD

        // example in FlipSwitch, where copy() calls set(), which should have been handled before
        MethodAnalysis.Eventual consistent = null;
        Block body = analyserContext.getMethodInspection(methodAnalyser.methodInfo).getMethodBody();
        Set<MethodInfo> calledMethods = new CallsToOwnMethods(analyserContext).visit(body).getMethods();
        for (MethodInfo calledMethod : calledMethods) {
            MethodAnalysis calledMethodAnalysis = analyserContext.getMethodAnalysis(calledMethod);
            if (calledMethodAnalysis.eventualIsSet()) {
                MethodAnalysis.Eventual eventual = calledMethodAnalysis.getEventual();
                if (eventual != MethodAnalysis.NOT_EVENTUAL) {
                    if (consistent == null) consistent = eventual;
                    else if (!eventual.consistentWith(consistent)) {
                        consistent = null;
                        break;
                    }
                }
            } else {
                return null; // DELAYS
            }
        }
        if (consistent != null) {
            return consistent.mark();
        }
        return false;
    }


    private static Boolean remapReturnIncompatible(EvaluationContext evaluationContext, Variable variable, Expression value,
                                                   Expression precondition) {
        Map<Expression, Expression> map = Map.of(new VariableExpression(variable), value);
        Expression reEvaluated = precondition.reEvaluate(evaluationContext, map).getExpression();
        // false ~ incompatible with precondition
        if (reEvaluated.isBooleanConstant()) return reEvaluated.isBoolValueFalse();
        return null;
    }

    private static boolean isCompatible(EvaluationContext evaluationContext, Expression v1, Expression v2) {
        Expression and = new And(evaluationContext.getPrimitives()).append(evaluationContext, v1, v2);
        return v1.equals(and) || v2.equals(and);
    }
}
