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
                            Expression state = statementAnalysis.stateData.getConditionManagerForNextStatement()
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
                        Expression state = statementAnalysis.stateData.getConditionManagerForNextStatement().state();
                        Expression combined = new And(evaluationContext.getPrimitives()).append(evaluationContext, state, notNull);

                        if (isCompatible(evaluationContext, combined, precondition)) {
                            if (statementAnalysis.stateData.conditionManagerIsNotYetSet()) {
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
            MethodAnalyser calledMethodAnalyser = analyserContext.getMethodAnalyser(calledMethod);
            if (calledMethodAnalyser.methodAnalysis.eventualIsSet()) {
                MethodAnalysis.Eventual eventual = calledMethodAnalyser.methodAnalysis.getEventual();
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
