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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.expression.util.EvaluateParameters;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.model.value.StringValue;
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
        super(methodTypeParameterMap.methodInfo, methodTypeParameterMap.getConcreteReturnType());
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
                    currentPrimaryType.typeResolution.get().isPartOfDependencyCycle(methodPrimaryType);

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
        VariableValue variableValue;
        if (modified == Level.TRUE && (variableValue = objectValue.asInstanceOf(VariableValue.class)) != null) {
            builder.modifyingMethodAccess(variableValue.variable);
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

            EvaluationResult mv = methodValue(evaluationContext, methodInfo, methodAnalysis, objectValue, parameterValues, objectFlowOfResult);
            builder.compose(mv);
            result = mv.value;
        } else {
            result = UnknownValue.NO_RETURN_VALUE;
        }
        builder.setValue(result);

        checkCommonErrors(builder, evaluationContext, objectValue);

        return builder.build();
    }


    private int notNullRequirementOnScope(int notNullRequirement) {
        if (methodInfo.typeInfo.isFunctionalInterface() && MultiLevel.isEffectivelyNotNull(notNullRequirement)) {
            return MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL; // @NotNull1
        }
        return MultiLevel.EFFECTIVELY_NOT_NULL;
    }

    private void checkOnly(EvaluationResult.Builder builder, ObjectFlow objectFlow) {
        Optional<AnnotationExpression> oOnly = methodInfo.methodInspection.get().annotations.stream()
                .filter(ae -> ae.typeInfo.fullyQualifiedName.equals(Only.class.getName())).findFirst();
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

    // static, also used in MethodValue re-evaluation
    public static EvaluationResult methodValue(EvaluationContext evaluationContext,
                                               MethodInfo methodInfo,
                                               MethodAnalysis methodAnalysis,
                                               Value objectValue,
                                               List<Value> parameters,
                                               ObjectFlow objectFlowOfResult) {
        EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);

        Objects.requireNonNull(evaluationContext);

        // no value (method call on field that does not have effective value yet)
        if (objectValue == UnknownValue.NO_VALUE) {
            return builder.setValue(UnknownValue.NO_VALUE).build(); // this will delay
        }

        // eval on constant, like "abc".length()
        Value evaluationOnConstant = computeEvaluationOnConstant(evaluationContext.getPrimitives(),
                methodInfo, objectValue);
        if (evaluationOnConstant != null) {
            return builder.setValue(evaluationOnConstant).build();
        }

        // @Identity as method annotation
        Value identity = computeIdentity(evaluationContext, methodAnalysis, parameters, objectFlowOfResult);
        if (identity != null) {
            return builder.setValue(identity).build();
        }

        // @Fluent as method annotation
        // fluent methods are modifying
        Value fluent = computeFluent(methodAnalysis, objectValue);
        if (fluent != null) {
            return builder.setValue(fluent).build();
        }

        InlineValue inlineValue;
        if (methodInfo.typeInfo.isFunctionalInterface() &&
                (inlineValue = objectValue.asInstanceOf(InlineValue.class)) != null &&
                inlineValue.canBeApplied(evaluationContext)) {
            Map<Value, Value> translationMap = EvaluateParameters.translationMap(evaluationContext, methodInfo, parameters);
            EvaluationResult reInline = inlineValue.reEvaluate(evaluationContext, translationMap);
            return builder.compose(reInline).setValue(reInline.value).build();
        }

        // singleReturnValue implies non-modifying
        if (methodAnalysis.isBeingAnalysed() && methodAnalysis.getSingleReturnValue() != null) {
            // if this method was identity?
            Value srv = methodAnalysis.getSingleReturnValue();
            if (srv.isInstanceOf(InlineValue.class)) {
                InlineValue iv = srv.asInstanceOf(InlineValue.class);

                // special situation
                // we have an instance object, like new Pair("a", "b"), and then a getter applying to this instance object
                // this we can resolve immediately
                if (objectValue instanceof Instance && iv.value instanceof VariableValue) {
                    Variable variable = ((VariableValue) iv.value).variable;
                    if (variable instanceof FieldReference) {
                        FieldInfo fieldInfo = ((FieldReference) variable).fieldInfo;
                        FieldAnalysis fieldAnalysis = evaluationContext.getFieldAnalysis(fieldInfo);
                        if (fieldAnalysis.getProperty(VariableProperty.FINAL) == Level.TRUE) {
                            Instance instance = (Instance) objectValue;
                            int i = 0;
                            List<ParameterAnalysis> parameterAnalyses = evaluationContext
                                    .getParameterAnalyses(instance.constructor).collect(Collectors.toList());
                            for (ParameterAnalysis parameterAnalysis : parameterAnalyses) {
                                if (parameterAnalysis.getAssignedToField() == fieldInfo) {
                                    return builder.setValue(instance.constructorParameterValues.get(i)).build();
                                }
                                i++;
                            }
                        }
                    }
                }
                Map<Value, Value> translationMap = EvaluateParameters.translationMap(evaluationContext,
                        methodInfo, parameters);
                EvaluationResult reSrv = srv.reEvaluate(evaluationContext, translationMap);
                return builder.compose(reSrv).setValue(reSrv.value).build();
            }
            if (srv.isConstant()) {
                return builder.setValue(srv).build();
            }
        } else if (methodAnalysis.isBeingAnalysed()) {
            // we will, at some point, analyse this method
            return builder.setValue(UnknownValue.NO_VALUE).build();
        }

        // normal method value
        MethodValue methodValue = new MethodValue(methodInfo, objectValue, parameters, objectFlowOfResult);
        return builder.setValue(methodValue).build();
    }

    private static Value computeEvaluationOnConstant(Primitives primitives, MethodInfo methodInfo, Value objectValue) {
        if (!objectValue.isConstant()) return null;
        StringValue stringValue;
        if ("java.lang.String.length()".equals(methodInfo.fullyQualifiedName()) &&
                (stringValue = objectValue.asInstanceOf(StringValue.class)) != null) {
            return new IntValue(primitives, stringValue.value.length(), ObjectFlow.NO_FLOW);
        }
        return null;
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
                                "Result of method call " + methodInspection.distinguishingName);
                    }
                } // else: delaying is fine
            }
        } // else: we've already requested this from the scope (functional interface)
    }

    private static Value computeFluent(MethodAnalysis methodAnalysis, Value scope) {
        int fluent = methodAnalysis.getProperty(VariableProperty.FLUENT);
        if (fluent == Level.DELAY && methodAnalysis.isBeingAnalysed()) return UnknownValue.NO_VALUE;
        if (fluent != Level.TRUE) return null;
        return scope;
    }


    private static Value computeIdentity(EvaluationContext evaluationContext,
                                         MethodAnalysis methodAnalysis,
                                         List<Value> parameters,
                                         ObjectFlow objectFlowOfResult) {
        int identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
        if (identity == Level.DELAY && methodAnalysis.isBeingAnalysed()) return UnknownValue.NO_VALUE; // delay
        if (identity != Level.TRUE) return null;

        Map<VariableProperty, Integer> map = new HashMap<>();
        for (VariableProperty property : VariableProperty.PROPERTIES_IN_METHOD_RESULT_WRAPPER) {
            int v = methodAnalysis.getProperty(property);
            if (v != Level.DELAY) map.put(property, v);
        }
        return PropertyWrapper.propertyWrapper(evaluationContext, parameters.get(0), map, objectFlowOfResult);
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
            if (methodInfo.isStatic) {
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
