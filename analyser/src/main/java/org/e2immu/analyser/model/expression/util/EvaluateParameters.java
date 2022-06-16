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

package org.e2immu.analyser.model.expression.util;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analyser.delay.DelayFactory;
import org.e2immu.analyser.analyser.delay.SimpleCause;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.model.Expression;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterInfo;
import org.e2immu.analyser.model.expression.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.ReturnVariable;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.util.Pair;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EvaluateParameters {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(EvaluateParameters.class);
    private static final Map<Property, DV> RECURSIVE_CALL =
            Map.of(Property.CONTEXT_MODIFIED, DV.FALSE_DV,
                    Property.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV);

    public static Pair<EvaluationResult.Builder, List<Expression>> transform(List<Expression> parameterExpressions,
                                                                             EvaluationResult context,
                                                                             ForwardEvaluationInfo forwardEvaluationInfo,
                                                                             MethodInfo methodInfo,
                                                                             boolean recursiveOrPartOfCallCycle,
                                                                             Expression scopeObject,
                                                                             boolean allowUpgradeCnnOfScope) {
        int n = methodInfo == null ? 10 : methodInfo.methodInspection.get().getParameters().size();
        List<Expression> parameterValues = new ArrayList<>(n);
        int i = 0;
        DV minCnnOverParameters = MultiLevel.EFFECTIVELY_NOT_NULL_DV;

        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);

        DV scopeIsContainer = scopeObject == null || recursiveOrPartOfCallCycle ? MultiLevel.NOT_CONTAINER_DV
                : context.evaluationContext().getProperty(scopeObject, Property.CONTAINER, true, true);

        for (Expression parameterExpression : parameterExpressions) {
            minCnnOverParameters = oneParameterReturnCnn(context, forwardEvaluationInfo,
                    methodInfo, recursiveOrPartOfCallCycle, parameterValues, i, builder, parameterExpression,
                    scopeIsContainer);
            i++;
        }

        VariableExpression scopeVariable;
        if (allowUpgradeCnnOfScope &&
                minCnnOverParameters.ge(MultiLevel.EFFECTIVELY_NOT_NULL_DV) &&
                i > 0 &&
                methodInfo != null &&
                scopeObject != null &&
                methodInfo.typeInfo.typeInspection.get().isFunctionalInterface() &&
                (scopeVariable = scopeObject.asInstanceOf(VariableExpression.class)) != null) {
            builder.setProperty(scopeVariable.variable(), Property.CONTEXT_NOT_NULL,
                    MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL_DV);
        }
        return new Pair<>(builder, parameterValues);
    }

    private static DV oneParameterReturnCnn(EvaluationResult context,
                                            ForwardEvaluationInfo forwardEvaluationInfo,
                                            MethodInfo methodInfo,
                                            boolean recursiveOrPartOfCallCycle,
                                            List<Expression> parameterValues,
                                            int position,
                                            EvaluationResult.Builder builder,
                                            Expression parameterExpression,
                                            DV scopeIsContainer) {
        Expression parameterValue;
        EvaluationResult parameterResult;
        DV contextNotNull;
        DV contextModified;
        if (methodInfo != null) {
            ParameterInfo parameterInfo = getParameterInfo(methodInfo, position);
            // NOT_NULL, NOT_MODIFIED
            Map<Property, DV> map;
            try {
                MethodAnalyser currentMethod = context.getCurrentMethod();
                if (currentMethod != null &&
                        currentMethod.getMethodInfo() == methodInfo) {
                    map = new HashMap<>(RECURSIVE_CALL);
                } else {
                    // copy from parameter into map used for forwarding
                    ParameterAnalysis parameterAnalysis = context.getAnalyserContext().getParameterAnalysis(parameterInfo);
                    map = new HashMap<>();
                    map.put(Property.CONTEXT_MODIFIED, parameterAnalysis.getProperty(Property.MODIFIED_VARIABLE));
                    map.put(Property.CONTEXT_NOT_NULL, parameterAnalysis.getProperty(Property.NOT_NULL_PARAMETER));
                    map.put(Property.CONTEXT_CONTAINER, parameterAnalysis.getProperty(Property.CONTAINER));
                }
            } catch (RuntimeException e) {
                LOGGER.error("Failed to obtain parameter analysis of {}", parameterInfo.fullyQualifiedName());
                throw e;
            }
            List<Variable> vars = parameterExpression.variables(true);
            boolean self = vars.stream().anyMatch(v -> {
                if (v instanceof FieldReference fr && fr.fieldInfo.owner == methodInfo.typeInfo) {
                    FieldAnalysis fieldAnalysis = context.getAnalyserContext().getFieldAnalysis(fr.fieldInfo);
                    return fieldAnalysis.getLinkedVariables().isDone() &&
                            fieldAnalysis.getLinkedVariables().contains(parameterInfo);
                }
                return false;
            });
            // FIXME REMOVED BREAK
            boolean recursivePartOfCallSelf = recursiveOrPartOfCallCycle;//|| self;
            computeContextContainer(methodInfo, recursivePartOfCallSelf, map);
            computeContextModified(methodInfo, recursivePartOfCallSelf, map, scopeIsContainer);

            contextNotNull = map.getOrDefault(Property.CONTEXT_NOT_NULL, null);
            if (recursivePartOfCallSelf) {
                map.put(Property.CONTEXT_NOT_NULL, MultiLevel.NULLABLE_DV); // won't be me to rock the boat
                if (contextNotNull.gt(MultiLevel.NULLABLE_DV)) {
                    String msg;
                    if (parameterExpression instanceof VariableExpression ve) {
                        msg = "Variable " + ve.variable().simpleName();
                    } else {
                        msg = "";
                    }
                    builder.raiseError(parameterExpression.getIdentifier(), Message.Label.CALL_CYCLE_NOT_NULL,
                            msg);
                }
            }

            ForwardEvaluationInfo forward = forwardEvaluationInfo.copy().setNotAssignmentTarget()
                    .addProperties(map).build();
            parameterResult = parameterExpression.evaluate(context, forward);
            parameterValue = parameterResult.value();
            contextModified = map.getOrDefault(Property.CONTEXT_MODIFIED, DV.FALSE_DV);
        } else {
            parameterResult = parameterExpression.evaluate(context, ForwardEvaluationInfo.DEFAULT);
            parameterValue = parameterResult.value();
            contextNotNull = Property.CONTEXT_NOT_NULL.bestDv;
            contextModified = Property.CONTEXT_MODIFIED.bestDv;
        }
        builder.compose(parameterResult);

        Expression afterModification;
        if (!contextModified.valueIsFalse()) {
            EvaluationResult er = potentiallyModifyConstructorCall(context, parameterExpression,
                    parameterValue, contextModified);
            if (er != null) {
                afterModification = er.getExpression() == null ? parameterValue : er.getExpression();
                builder.compose(er);
            } else {
                afterModification = parameterValue;
            }
        } else {
            afterModification = parameterValue;
        }
        assert afterModification != null;
        parameterValues.add(afterModification);
        return contextNotNull;
    }

    /*
    See e.g. Modification_23: if the parameter is modified, and the argument is a variable (or an expression linked
    to a variable) whose value is a constructor call, this constructor should change into an instance, much in the same
    way as this happens for objects upon which a modifying method is called (see MethodCall).

    we have to handle (1) the variable if parameterExpression is a variable, and (2) all variables referred to
    in parameterValue.
    IMPORTANT: we're not allowed to change linked variables!
     */
    private static EvaluationResult potentiallyModifyConstructorCall(EvaluationResult context,
                                                                     Expression parameterExpression,
                                                                     Expression parameterValue,
                                                                     DV contextModified) {

        EvaluationResult.Builder builder = new EvaluationResult.Builder(context);
        LinkedVariables linkedVariables1 = parameterExpression.linkedVariables(context);
        LinkedVariables linkedVariables2 = parameterValue.linkedVariables(context);
        LinkedVariables linkedVariables = linkedVariables1.merge(linkedVariables2);
        IsVariableExpression ive;
        Variable theVariable = (ive = parameterExpression.asInstanceOf(IsVariableExpression.class)) != null ? ive.variable() : null;
        boolean changed = false;
        for (Map.Entry<Variable, DV> e : linkedVariables.variables().entrySet()) {
            DV dv = e.getValue();
            Variable variable = e.getKey();
            changed |= potentiallyChangeOneVariable(context, parameterValue, contextModified, builder,
                    linkedVariables1, linkedVariables2, theVariable, dv, variable);
        }
        if (changed) {
            return builder.build();
        }
        return null;
    }

    private static boolean potentiallyChangeOneVariable(EvaluationResult context,
                                                        Expression parameterValue,
                                                        DV contextModified,
                                                        EvaluationResult.Builder builder,
                                                        LinkedVariables linkedVariables1,
                                                        LinkedVariables linkedVariables2,
                                                        Variable theVariable,
                                                        DV dvLink,
                                                        Variable variable) {
        if (variable instanceof This || variable instanceof ReturnVariable
                || !context.evaluationContext().isPresent(variable)){
            return false;
        }
        if (dvLink.isDone() && parameterValue.isDone() && contextModified.valueIsTrue()) {
            if (dvLink.le(LinkedVariables.DEPENDENT_DV)) {
                Expression varVal = context.currentValue(variable);
                ConstructorCall cc;
                Expression newInstance;
                if ((cc = varVal.asInstanceOf(ConstructorCall.class)) != null && cc.constructor() != null) {
                    Properties valueProperties = context.evaluationContext().getValueProperties(cc);
                    newInstance = Instance.forMethodResult(cc.identifier, cc.returnType(), valueProperties);
                } else if (varVal.hasState() && varVal instanceof PropertyWrapper pw) {
                    // drop this state -- IMPROVE we won't do any companion code here at the moment
                    newInstance = pw.unwrapState();
                } else {
                    newInstance = null;
                }
                if (newInstance != null) {
                    LinkedVariables lv = variable == theVariable
                            ? linkedVariables1
                            : context.evaluationContext().linkedVariables(variable);
                    builder.modifyingMethodAccess(variable, newInstance, lv);
                    return true;
                }
            } // else: this variable is not affected
        } else {
            // delay the end result
            CausesOfDelay delayMarker = DelayFactory.createDelay(new SimpleCause(context.evaluationContext().getLocation(Stage.EVALUATION),
                    CauseOfDelay.Cause.CONSTRUCTOR_TO_INSTANCE));
            CausesOfDelay merge = dvLink.causesOfDelay().merge(parameterValue.causesOfDelay())
                    .merge(contextModified.causesOfDelay()).merge(delayMarker);
            Expression delayed = parameterValue.isDone() ? DelayedExpression.forModification(parameterValue, merge)
                    : parameterValue;
            LinkedVariables lv = variable == theVariable ? linkedVariables1 : linkedVariables2;
            builder.modifyingMethodAccess(variable, delayed, lv);
            return true;
        }
        return false;
    }

    private static void computeContextContainer(MethodInfo methodInfo,
                                                boolean recursiveOrPartOfCallCycle,
                                                Map<Property, DV> map) {
        if (recursiveOrPartOfCallCycle) {
            map.put(Property.CONTEXT_CONTAINER, MultiLevel.NOT_CONTAINER_DV);
        } else {
            DV contextContainer = map.getOrDefault(Property.CONTEXT_CONTAINER, null);
            if (contextContainer == null) {
                CausesOfDelay delay = methodInfo.delay(CauseOfDelay.Cause.CONTEXT_CONTAINER);
                map.put(Property.CONTEXT_CONTAINER, delay);
            }
        }
    }

    private static void computeContextModified(MethodInfo methodInfo,
                                               boolean recursiveOrPartOfCallCycle,
                                               Map<Property, DV> map,
                                               DV scopeIsContainer) {
        if (recursiveOrPartOfCallCycle || scopeIsContainer.equals(MultiLevel.CONTAINER_DV)) {
            map.put(Property.CONTEXT_MODIFIED, DV.FALSE_DV);
        } else if (scopeIsContainer.isDelayed()) {
            map.merge(Property.CONTEXT_MODIFIED, scopeIsContainer, DV::maxIgnoreDelay);
        } else {
            DV contextModified = map.getOrDefault(Property.CONTEXT_MODIFIED, null);
            if (contextModified == null) {
                CausesOfDelay delay = methodInfo.delay(CauseOfDelay.Cause.CONTEXT_MODIFIED);
                map.put(Property.CONTEXT_MODIFIED, delay);
            }
        }
    }

    private static ParameterInfo getParameterInfo(MethodInfo methodInfo, int position) {
        ParameterInfo parameterInfo;
        List<ParameterInfo> params = methodInfo.methodInspection.get().getParameters();
        if (position >= params.size()) {
            ParameterInfo lastParameter = params.get(params.size() - 1);
            if (lastParameter.parameterInspection.get().isVarArgs()) {
                parameterInfo = lastParameter;
            } else {
                throw new UnsupportedOperationException("?");
            }
        } else {
            parameterInfo = params.get(position);
        }
        return parameterInfo;
    }
}
