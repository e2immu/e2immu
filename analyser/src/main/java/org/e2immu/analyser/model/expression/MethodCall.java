/*
 * e2immu-analyser: code analyser for effective and eventual immutability
 * Copyright 2020, Bart Naudts, https://www.e2immu.org
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package org.e2immu.analyser.model.expression;

import com.google.common.collect.ImmutableMap;
import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.inspector.MethodTypeParameterMap;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.EvaluateMethodCall;
import org.e2immu.analyser.model.expression.util.EvaluateParameters;
import org.e2immu.analyser.model.value.*;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.objectflow.access.MethodAccess;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.ListUtil;
import org.e2immu.analyser.util.Logger;
import org.e2immu.analyser.util.Pair;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Only;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;


public class MethodCall extends ExpressionWithMethodReferenceResolution implements HasParameterExpressions {
    private static final org.slf4j.Logger LOGGER = LoggerFactory.getLogger(MethodCall.class);

    public final Expression object;
    public final Expression computedScope;
    public final List<Expression> parameterExpressions;
    public final MethodTypeParameterMap methodTypeParameterMap;

    public MethodCall(@NotNull Expression object,
                      @NotNull Expression computedScope,
                      @NotNull MethodTypeParameterMap methodTypeParameterMap,
                      @NotNull List<Expression> parameterExpressions) {
        super(methodTypeParameterMap.methodInspection.getMethodInfo(), methodTypeParameterMap.getConcreteReturnType());
        this.object = object;
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        this.computedScope = Objects.requireNonNull(computedScope);
        this.methodTypeParameterMap = methodTypeParameterMap;
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new MethodCall(object == null ? null : translationMap.translateExpression(object),
                translationMap.translateExpression(computedScope),
                methodTypeParameterMap.translate(translationMap),
                parameterExpressions.stream().map(translationMap::translateExpression).collect(Collectors.toList()));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodCall that = (MethodCall) o;
        return computedScope.equals(that.computedScope) &&
                parameterExpressions.equals(that.parameterExpressions) &&
                methodTypeParameterMap.equals(that.methodTypeParameterMap);
    }

    @Override
    public int hashCode() {
        return Objects.hash(computedScope, parameterExpressions, methodTypeParameterMap);
    }

    @Override
    public List<Expression> getParameterExpressions() {
        return parameterExpressions;
    }

    @Override
    public MethodInfo getMethodInfo() {
        return methodInfo;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);

        // potential circular reference?
        boolean alwaysModifying;
        boolean delayUndeclared = false;

        if (evaluationContext.getCurrentMethod() != null) {
            TypeInfo currentPrimaryType = evaluationContext.getCurrentType().primaryType();
            TypeInfo methodPrimaryType = methodInfo.typeInfo.primaryType();

            boolean circularCall = methodPrimaryType != currentPrimaryType &&
                    currentPrimaryType.typeResolution.get().circularDependencies().contains(methodPrimaryType) &&
                    !ShallowTypeAnalyser.IS_FACT_FQN.equals(methodInfo.fullyQualifiedName());

            boolean undeclaredFunctionalInterface;
            if (methodInfo.isSingleAbstractMethod()) {
                Boolean b = EvaluateParameters.tryToDetectUndeclared(evaluationContext, computedScope);
                undeclaredFunctionalInterface = b != null && b;
                delayUndeclared = b == null;
            } else {
                undeclaredFunctionalInterface = false;
            }
            if ((circularCall || undeclaredFunctionalInterface)) {
                builder.addCircularCallOrUndeclaredFunctionalInterface();
            }
            alwaysModifying = circularCall || undeclaredFunctionalInterface;
        } else {
            alwaysModifying = false;
        }
        MethodAnalysis methodAnalysis;
        try {
            methodAnalysis = evaluationContext.getMethodAnalysis(methodInfo);
        } catch (UnsupportedOperationException e) {
            LOGGER.warn("Error obtaining method analysis for {}", methodInfo.fullyQualifiedName());
            throw e;
        }
        // is the method modifying, do we need to wait?
        int modified = alwaysModifying ? Level.TRUE : methodAnalysis.getProperty(VariableProperty.MODIFIED);
        int methodDelay = Level.fromBool(modified == Level.DELAY || delayUndeclared);

        // effectively not null is the default, but when we're in a not null situation, we can demand effectively content not null
        int notNullForward = notNullRequirementOnScope(forwardEvaluationInfo.getProperty(VariableProperty.NOT_NULL));
        boolean contentNotNullRequired = notNullForward == MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;

        // scope
        EvaluationResult objectResult = computedScope.evaluate(evaluationContext, new ForwardEvaluationInfo(Map.of(
                VariableProperty.NOT_NULL, notNullForward,
                VariableProperty.METHOD_CALLED, Level.TRUE,
                VariableProperty.METHOD_DELAY, methodDelay,
                VariableProperty.MODIFIED, modified), true));

        // null scope
        Value objectValue = objectResult.value;
        if (objectValue.isInstanceOf(NullValue.class)) {
            builder.raiseError(Message.NULL_POINTER_EXCEPTION);
        }

        // process parameters
        int notModified1Scope = evaluationContext.getProperty(objectValue, VariableProperty.NOT_MODIFIED_1);
        Pair<EvaluationResult.Builder, List<Value>> res = EvaluateParameters.transform(parameterExpressions, evaluationContext, methodInfo, notModified1Scope, objectValue);
        List<Value> parameterValues = res.v;
        builder.compose(objectResult, res.k.build());

        if (parameterValues.stream().anyMatch(pv -> pv == UnknownValue.NO_VALUE)) {
            Logger.log(DELAYED, "Delayed method call because one of the parameter values is delayed: {}, {}", methodInfo.name, parameterValues);
            builder.setValue(UnknownValue.NO_VALUE);
            return builder.build();
        }

        // access
        ObjectFlow objectFlow = objectValue.getObjectFlow();
        if (objectFlow != ObjectFlow.NO_FLOW) {
            if (modified == Level.DELAY) {
                Logger.log(DELAYED, "Delaying flow access registration because modification status of {} not known",
                        methodInfo.fullyQualifiedName());
                objectFlow.delay();
            } else {
                List<ObjectFlow> flowsOfArguments = parameterValues.stream().map(Value::getObjectFlow).collect(Collectors.toList());
                MethodAccess methodAccess = new MethodAccess(methodInfo, flowsOfArguments);
                builder.addAccess(modified == Level.TRUE, methodAccess, objectValue);
            }
        }

        // companion methods
        Instance modifiedInstance;
        if (modified == Level.TRUE) {
            modifiedInstance = checkCompanionMethodsModifying(builder, evaluationContext, methodInfo,
                    methodAnalysis, objectValue, parameterValues);
        } else {
            modifiedInstance = null;
        }

        // @Only check
        checkOnly(builder, objectFlow);

        // return value
        Location location = evaluationContext.getLocation(this);
        ObjectFlow objectFlowOfResult;
        if (!Primitives.isVoid(methodInfo.returnType())) {
            ObjectFlow returnedFlow = methodAnalysis.getObjectFlow();

            objectFlowOfResult = builder.createInternalObjectFlow(location, methodInfo.returnType(), Origin.RESULT_OF_METHOD);
            objectFlowOfResult.addPrevious(returnedFlow);
            // cross-link, possible because returnedFlow is already permanent
            // TODO ObjectFlow check cross-link
            returnedFlow.addNext(objectFlowOfResult);
        } else {
            objectFlowOfResult = ObjectFlow.NO_FLOW;
        }

        Value result;
        if (!methodInfo.isVoid()) {
            MethodInspection methodInspection = methodInfo.methodInspection.get();
            complianceWithForwardRequirements(builder, methodAnalysis, methodInspection, forwardEvaluationInfo, contentNotNullRequired);

            EvaluationResult mv = EvaluateMethodCall.methodValue(modified, evaluationContext, methodInfo,
                    methodAnalysis, objectValue, parameterValues, objectFlowOfResult);
            builder.compose(mv);
            if (mv.value == objectValue && mv.value instanceof Instance && modifiedInstance != null) {
                result = modifiedInstance;
            } else {
                result = mv.value;
            }
        } else {
            result = UnknownValue.NO_RETURN_VALUE;
        }
        builder.setValue(result);

        checkCommonErrors(builder, evaluationContext, objectValue);

        return builder.build();
    }

    static Instance checkCompanionMethodsModifying(
            EvaluationResult.Builder builder,
            EvaluationContext evaluationContext,
            MethodInfo methodInfo,
            MethodAnalysis methodAnalysis,
            Value objectValue,
            List<Value> parameterValues) {
        Instance instance;
        if (objectValue instanceof VariableValue variableValue) {
            instance = builder.currentInstance(variableValue.variable, ObjectFlow.NO_FLOW, UnknownValue.EMPTY);
        } else {
            instance = objectValue.getInstance(evaluationContext);
        }
        Objects.requireNonNull(instance, "Modifying method on constant or primitive? Impossible");

        AtomicReference<Value> newState = new AtomicReference<>(instance.state);
        methodInfo.methodInspection.get().getCompanionMethods().keySet().stream()
                .filter(e -> CompanionMethodName.MODIFYING_METHOD_OR_CONSTRUCTOR.contains(e.action()))
                .sorted()
                .forEach(companionMethodName -> {
                    if (companionMethodName.action() == CompanionMethodName.Action.CLEAR) {
                        newState.set(UnknownValue.EMPTY);
                        return;
                    }
                    CompanionAnalysis companionAnalysis = methodAnalysis.getCompanionAnalyses().get(companionMethodName);
                    MethodInfo aspectMethod;
                    if (companionMethodName.aspect() != null) {
                        aspectMethod = evaluationContext.getTypeAnalysis(methodInfo.typeInfo).getAspects().get(companionMethodName.aspect());
                        assert aspectMethod != null : "Expect aspect method to be known";
                    } else {
                        aspectMethod = null;
                    }

                    // in the case of java.util.List.add(), the aspect is Size, there are 3+ "parameters":
                    // pre, post, and the parameter(s) of the add method.
                    // post is already OK (it is the new value of the aspect method)
                    // pre is the "old" value, which has to be obtained. If that's impossible, we bail out.
                    // the parameters are available
                    FilterResultAndTranslationMap translation = createTranslationMap(evaluationContext, aspectMethod,
                            companionAnalysis, newState.get(), methodInfo.isConstructor, parameterValues);

                    Value companionValue = companionAnalysis.getValue();
                    EvaluationContext child = evaluationContext.child(instance.state);
                    EvaluationResult companionValueTranslationResult = companionValue.reEvaluate(child, translation.translationMap);
                    // no need to compose: this is a separate operation. builder.compose(companionValueTranslationResult);
                    Value companionValueTranslated = companionValueTranslationResult.value;
                    // IMPROVE the object flow and the state from the precondition!!

                    if (translation.filterResult != null) {
                        // there is an old "pre" value that needs to be removed
                        if (translation.filterResult.rest() == UnknownValue.EMPTY) {
                            newState.set(companionValueTranslated);
                        } else {
                            newState.set(new AndValue(evaluationContext.getPrimitives()).append(evaluationContext, translation.filterResult.rest(),
                                    companionValueTranslated));
                        }
                    } else {
                        // no pre-value to be removed
                        if (newState.get() == UnknownValue.EMPTY) {
                            newState.set(companionValueTranslated);
                        } else {
                            newState.set(new AndValue(evaluationContext.getPrimitives()).append(evaluationContext, newState.get(),
                                    companionValueTranslated));
                        }
                    }
                });
        Instance modifiedInstance = new Instance(instance, newState.get());
        if (objectValue instanceof VariableValue variableValue) {
            Set<Variable> linkedVariables = variablesLinkedToScopeVariableInModifyingMethod(evaluationContext, parameterValues);
            builder.modifyingMethodAccess(variableValue.variable, modifiedInstance, linkedVariables);
        }
        return modifiedInstance;
    }

    /*
    Modifying method

    list.add(a);

    After this operation, list should be linked to a.

    Null value means delays, as per convention.
     */
    private static Set<Variable> variablesLinkedToScopeVariableInModifyingMethod(EvaluationContext evaluationContext,
                                                                                 List<Value> parameterValues) {
        Set<Variable> result = new HashSet<>();
        for (Value p : parameterValues) {
            Set<Variable> cd = evaluationContext.linkedVariables(p);
            if (cd == null) return null;
            result.addAll(cd);
        }
        return result;
    }

    // the "pre" clause
    private record FilterResultAndTranslationMap(Filter.FilterResult<MethodValue> filterResult,
                                                 Map<Value, Value> translationMap) {
    }

    // for modifying methods only. there is a "Pre" variable when there are aspects.
    // the filter result contains the pre-clause and the rest.
    private static FilterResultAndTranslationMap createTranslationMap(EvaluationContext evaluationContext,
                                                                      MethodInfo aspectMethod,
                                                                      CompanionAnalysis companionAnalysis,
                                                                      Value stateOfInstance,
                                                                      boolean mainMethodIsConstructor,
                                                                      List<Value> parameterValues) {
        ImmutableMap.Builder<Value, Value> translationMap = new ImmutableMap.Builder<>();
        Filter.FilterResult<MethodValue> filterResult;
        Value preAspectVariableValue = companionAnalysis.getPreAspectVariableValue();

        if (aspectMethod != null && !mainMethodIsConstructor) {
            // first: pre
            filterResult = EvaluateMethodCall.filter(evaluationContext, aspectMethod, stateOfInstance, List.of());
            translationMap.put(preAspectVariableValue, filterResult.accepted().values().stream()
                    .findFirst()
                    // it is possible that no pre- information can be found... that's OK as long as it isn't used
                    .orElse(UnknownValue.EMPTY));
        } else {
            filterResult = null;
        }
        // parameters
        ListUtil.joinLists(companionAnalysis.getParameterValues(), parameterValues).forEach(pair -> translationMap.put(pair.k, pair.v));
        return new FilterResultAndTranslationMap(filterResult, translationMap.build());
    }


    private int notNullRequirementOnScope(int notNullRequirement) {
        if (methodInfo.typeInfo.typeInspection.get().isFunctionalInterface() && MultiLevel.isEffectivelyNotNull(notNullRequirement)) {
            return MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL; // @NotNull1
        }
        return MultiLevel.EFFECTIVELY_NOT_NULL;
    }

    private void checkOnly(EvaluationResult.Builder builder, ObjectFlow objectFlow) {
        Optional<AnnotationExpression> oOnly = methodInfo.methodInspection.get().getAnnotations().stream()
                .filter(ae -> ae.typeInfo().fullyQualifiedName.equals(Only.class.getName())).findFirst();
        if (oOnly.isPresent()) {
            AnnotationExpression ae = oOnly.get();
            String before = ae.extract("before", "");
            if (!before.isEmpty()) {
                Set<String> marks = objectFlow.marks();
                if (marks.contains(before)) {
                    builder.raiseError(Message.ONLY_BEFORE, methodInfo.fullyQualifiedName() +
                            ", mark \"" + before + "\"");
                }
            } else {
                String after = ae.extract("after", "");
                Set<String> marks = objectFlow.marks();
                if (!marks.contains(after)) {
                    builder.raiseError(Message.ONLY_AFTER, methodInfo.fullyQualifiedName() +
                            ", mark \"" + after + "\"");
                }
            }
        }
    }

    private void checkCommonErrors(EvaluationResult.Builder builder, EvaluationContext evaluationContext, Value objectValue) {
        if (methodInfo.fullyQualifiedName().equals("java.lang.String.toString()")) {
            ParameterizedType type = objectValue.type();
            if (type != null && type.typeInfo != null && type.typeInfo ==
                    evaluationContext.getPrimitives().stringTypeInfo) {
                builder.raiseError(Message.UNNECESSARY_METHOD_CALL);
            }
        }

        MethodInfo method;
        if (objectValue instanceof InlineValue) {
            method = ((InlineValue) objectValue).methodInfo;
        } else {
            method = methodInfo;
        }
        MethodAnalysis methodAnalysis = evaluationContext.getMethodAnalysis(method);
        int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
        int immutable = evaluationContext.getProperty(objectValue, VariableProperty.IMMUTABLE);
        if (modified == Level.TRUE && immutable >= MultiLevel.EVENTUALLY_E2IMMUTABLE) {
            builder.raiseError(Message.CALLING_MODIFYING_METHOD_ON_E2IMMU,
                    "Method: " + methodInfo.distinguishingName() + ", Type: " + objectValue.type());
        }
    }

    private static void complianceWithForwardRequirements(EvaluationResult.Builder builder,
                                                          MethodAnalysis methodAnalysis,
                                                          MethodInspection methodInspection,
                                                          ForwardEvaluationInfo forwardEvaluationInfo,
                                                          boolean contentNotNullRequired) {
        if (!contentNotNullRequired) {
            int requiredNotNull = forwardEvaluationInfo.getProperty(VariableProperty.NOT_NULL);
            if (MultiLevel.isEffectivelyNotNull(requiredNotNull)) {
                int methodNotNull = methodAnalysis.getProperty(VariableProperty.NOT_NULL);
                if (methodNotNull != Level.DELAY) {
                    boolean isNotNull = MultiLevel.isEffectivelyNotNull(methodNotNull);
                    if (!isNotNull) {
                        builder.raiseError(Message.POTENTIAL_NULL_POINTER_EXCEPTION,
                                "Result of method call " + methodInspection.getFullyQualifiedName());
                    }
                } // else: delaying is fine
            }
        } // else: we've already requested this from the scope (functional interface)
    }

    @Override
    public String expressionString(int indent) {
        String scope = "";
        if (object != null) {
            scope = bracketedExpressionString(indent, object) + ".";
        }
        return scope + methodInfo.name +
                "(" + parameterExpressions.stream().map(expression -> expression.expressionString(indent))
                .collect(Collectors.joining(", ")) + ")";
    }

    @Override
    public int precedence() {
        return 16;
    }

    @Override
    public List<? extends Element> subElements() {
        return ListUtil.immutableConcat(parameterExpressions, List.of(computedScope));
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        Objects.requireNonNull(evaluationContext);

        SideEffect params = parameterExpressions.stream()
                .map(e -> e.sideEffect(evaluationContext))
                .reduce(SideEffect.LOCAL, SideEffect::combine);

        // look at the object... if it is static, we're in the same boat
        if (object instanceof FieldAccess fieldAccess) {
            if (fieldAccess.variable.isStatic() && params.lessThan(SideEffect.SIDE_EFFECT))
                return SideEffect.STATIC_ONLY;
        }
        if (object instanceof VariableExpression variableExpression) {
            if (variableExpression.variable.isStatic() && params.lessThan(SideEffect.SIDE_EFFECT))
                return SideEffect.STATIC_ONLY;
        }
        if (object != null) {
            SideEffect sideEffect = object.sideEffect(evaluationContext);
            if (sideEffect == SideEffect.STATIC_ONLY && params.lessThan(SideEffect.SIDE_EFFECT)) {
                return SideEffect.STATIC_ONLY;
            }
        }

        SideEffect methodsSideEffect = sideEffectNotTakingEventualIntoAccount(evaluationContext);
        if (methodsSideEffect == SideEffect.STATIC_ONLY && params.lessThan(SideEffect.SIDE_EFFECT)) {
            return SideEffect.STATIC_ONLY;
        }
        return methodsSideEffect.combine(params);
    }

    private SideEffect sideEffectNotTakingEventualIntoAccount(EvaluationContext evaluationContext) {
        MethodAnalysis methodAnalysis = evaluationContext.getMethodAnalysis(methodInfo);
        int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);

        TypeAnalysis typeAnalysis = evaluationContext.getTypeAnalysis(methodInfo.typeInfo);
        int immutable = typeAnalysis.getProperty(VariableProperty.IMMUTABLE);

        boolean effectivelyE2Immutable = immutable == MultiLevel.EFFECTIVELY_E2IMMUTABLE;
        if (!effectivelyE2Immutable && modified == Level.DELAY) return SideEffect.DELAYED;
        if (effectivelyE2Immutable || modified == Level.FALSE) {
            if (methodInfo.methodInspection.get().isStatic()) {
                if (methodInfo.isVoid()) {
                    return SideEffect.STATIC_ONLY;
                }
                return SideEffect.NONE_PURE;
            }
            return SideEffect.NONE_CONTEXT;
        }
        return SideEffect.SIDE_EFFECT;
    }
}
