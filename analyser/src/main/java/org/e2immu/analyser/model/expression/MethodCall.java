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
import org.e2immu.analyser.model.value.BoolValue;
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

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.e2immu.analyser.util.Logger.LogTarget.SIZE;
import static org.e2immu.analyser.util.Logger.log;

public class MethodCall extends ExpressionWithMethodReferenceResolution implements HasParameterExpressions {
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
            TypeInfo currentPrimaryType = evaluationContext.getCurrentType().primaryType;

            assert currentPrimaryType.typeResolution.get().circularDependencies.isSet() :
                    "Circular dependencies of type " + currentPrimaryType.fullyQualifiedName + " not yet set";

            boolean circularCall = currentPrimaryType.typeResolution.get().circularDependencies.get().contains(methodInfo.typeInfo);
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

        MethodAnalysis methodAnalysis = evaluationContext.getMethodAnalysis(methodInfo);

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

            EvaluationResult mv = methodValue(evaluationContext, location, methodInfo, methodAnalysis, objectValue, parameterValues, objectFlowOfResult);
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
                                               Location location,
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

        // @Size as method annotation
        Value sizeShortCut = computeSize(builder, methodInfo, methodAnalysis, objectValue, parameters, evaluationContext, location);
        if (sizeShortCut != null) {
            return builder.setValue(sizeShortCut).build();
        }

        // @Identity as method annotation
        Value identity = computeIdentity(evaluationContext, methodAnalysis, parameters, objectFlowOfResult);
        if (identity != null) {
            return builder.setValue(identity).build();
        }

        // @Fluent as method annotation
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

        if (methodAnalysis.isHasBeenDefined() && methodAnalysis.getSingleReturnValue() != null) {
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
        } else if (methodAnalysis.isHasBeenDefined()) {
            // we will, at some point, analyse this method
            return builder.setValue(UnknownValue.NO_VALUE).build();
        }

        // normal method value
        return builder.setValue(new MethodValue(evaluationContext.getPrimitives(),
                methodInfo, objectValue, parameters, objectFlowOfResult)).build();
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

        int requiredSize = forwardEvaluationInfo.getProperty(VariableProperty.SIZE);
        if (requiredSize > Level.IS_A_SIZE) {
            int currentSize = methodAnalysis.getProperty(VariableProperty.SIZE);
            if (!Level.compatibleSizes(currentSize, requiredSize)) {
                builder.raiseError(Message.POTENTIAL_SIZE_PROBLEM);
            }
        }
    }

    private static Value computeFluent(MethodAnalysis methodAnalysis, Value scope) {
        int fluent = methodAnalysis.getProperty(VariableProperty.FLUENT);
        if (fluent == Level.DELAY && methodAnalysis.isHasBeenDefined()) return UnknownValue.NO_VALUE;
        if (fluent != Level.TRUE) return null;
        return scope;
    }


    private static Value computeIdentity(EvaluationContext evaluationContext,
                                         MethodAnalysis methodAnalysis,
                                         List<Value> parameters,
                                         ObjectFlow objectFlowOfResult) {
        int identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
        if (identity == Level.DELAY && methodAnalysis.isHasBeenDefined()) return UnknownValue.NO_VALUE; // delay
        if (identity != Level.TRUE) return null;

        Map<VariableProperty, Integer> map = new HashMap<>();
        for (VariableProperty property : VariableProperty.PROPERTIES_IN_METHOD_RESULT_WRAPPER) {
            int v = methodAnalysis.getProperty(property);
            if (v != Level.DELAY) map.put(property, v);
        }
        return PropertyWrapper.propertyWrapper(evaluationContext, parameters.get(0), map, objectFlowOfResult);
    }

    private static Value computeSize(EvaluationResult.Builder builder,
                                     MethodInfo methodInfo,
                                     MethodAnalysis methodAnalysis,
                                     Value objectValue,
                                     List<Value> parameters,
                                     EvaluationContext evaluationContext,
                                     Location location) {
        if (!methodInfo.typeInfo.hasSize(evaluationContext.getAnalyserContext()))
            return null;

        int modified = methodAnalysis.getProperty(VariableProperty.MODIFIED);
        if (modified == Level.DELAY) {
            return null; // ignore
        }
        if (modified == Level.TRUE) {
            Stream<ParameterAnalysis> parameterAnalyses = evaluationContext.getParameterAnalyses(methodInfo);

            return computeSizeModifyingMethod(builder, methodInfo, methodAnalysis, parameterAnalyses, objectValue, evaluationContext);
        }

        int requiredSize = methodAnalysis.getProperty(VariableProperty.SIZE);
        if (requiredSize <= Level.NOT_A_SIZE) return null;
        // we have an @Size annotation on the method that we're calling
        int sizeOfObject = evaluationContext.getProperty(objectValue, VariableProperty.SIZE);

        Primitives primitives = evaluationContext.getPrimitives();
        // SITUATION 1: @Size(equals = 0) boolean isEmpty() { }, @Size(min = 1) boolean isNotEmpty() {}, etc.
        MethodAnalysis sizeMethodAnalysis = evaluationContext.getMethodAnalysis(methodInfo.typeInfo
                .sizeMethod(evaluationContext.getAnalyserContext()));

        if (Primitives.isBooleanOrBoxedBoolean(methodInfo.returnType())) {
            // there is an @Size annotation on a method returning a boolean...
            if (sizeOfObject <= Level.IS_A_SIZE) {
                log(SIZE, "Required @Size is {}, but we have no information. Result could be true or false.");
                return sizeMethodValue(builder, location, evaluationContext, sizeMethodAnalysis, objectValue, requiredSize);
            }
            // we have a requirement, and we have a size.
            if (Level.haveEquals(requiredSize)) {
                // we require a fixed size.
                if (Level.haveEquals(sizeOfObject)) {
                    builder.raiseError(Message.METHOD_EVALUATES_TO_CONSTANT);
                    log(SIZE, "Required @Size is {}, and we have {}. Result is a constant true or false.", requiredSize, sizeOfObject);
                    return sizeOfObject == requiredSize ? BoolValue.createTrue(primitives) : BoolValue.createFalse(primitives);
                }
                if (sizeOfObject > requiredSize) {
                    log(SIZE, "Required @Size is {}, and we have {}. Result is always false.", requiredSize, sizeOfObject);
                    builder.raiseError(Message.METHOD_EVALUATES_TO_CONSTANT);
                    return BoolValue.createFalse(primitives);
                }
                log(SIZE, "Required @Size is {}, and we have {}. Result could be true or false.", requiredSize, sizeOfObject);
                return sizeMethodValue(builder, location, evaluationContext, sizeMethodAnalysis, objectValue, requiredSize); // we want =3, but we have >= 2; could be anything
            }
            if (sizeOfObject > requiredSize) {
                log(SIZE, "Required @Size is {}, and we have {}. Result is always true.", requiredSize, sizeOfObject);
                builder.raiseError(Message.METHOD_EVALUATES_TO_CONSTANT);
                return BoolValue.createFalse(primitives);
            }
            log(SIZE, "Required @Size is {}, and we have {}. Result could be true or false.", requiredSize, sizeOfObject);
            return sizeMethodValue(builder, location, evaluationContext, sizeMethodAnalysis, objectValue, requiredSize);
        }

        // SITUATION 2: @Size int size(): this method returns the size
        if (TypeInfo.returnsIntOrLong(methodInfo)) {
            if (Level.haveEquals(sizeOfObject)) {
                return new IntValue(primitives, Level.decodeSizeEquals(sizeOfObject), ObjectFlow.NO_FLOW);
            }
            ObjectFlow objectFlow = sizeObjectFlow(evaluationContext, builder, location, sizeMethodAnalysis, objectValue);
            return ConstrainedNumericValue.lowerBound(evaluationContext,
                    new MethodValue(primitives, methodInfo, objectValue, parameters, objectFlow),
                    Level.decodeSizeMin(sizeOfObject));
        }
        return null;
    }

    // instead of returning isEmpty(), we return size() == 0
    // instead of returning isNotEmpty(), we return size() >= 1
    private static Value sizeMethodValue(EvaluationResult.Builder builder,
                                         Location location,
                                         EvaluationContext evaluationContext,
                                         MethodAnalysis sizeMethodAnalysis, Value objectValue, int requiredSize) {
        MethodValue sizeMethod = createSizeMethodCheckForSizeCopyTrue(builder, location, sizeMethodAnalysis, objectValue, evaluationContext);
        if (Level.haveEquals(requiredSize)) {
            Primitives primitives = evaluationContext.getPrimitives();
            ConstrainedNumericValue constrainedSizeMethod = ConstrainedNumericValue.lowerBound(evaluationContext, sizeMethod, 0);
            ObjectFlow objectFlow = builder.createInternalObjectFlow(location, primitives.booleanParameterizedType, Origin.RESULT_OF_METHOD);
            return EqualsValue.equals(evaluationContext,
                    new IntValue(primitives, Level.decodeSizeEquals(requiredSize),
                            builder.createInternalObjectFlow(location, primitives.intParameterizedType, Origin.RESULT_OF_METHOD)),
                    constrainedSizeMethod, objectFlow);
        }
        return ConstrainedNumericValue.lowerBound(evaluationContext, sizeMethod, Level.decodeSizeMin(requiredSize));
    }

    // we normally return  objectValue.size();
    // but if objectValue is a method itself, and that method preserves the size, then we remove that method
    // object.entrySet().size() == object.size(); the entrySet() method has a @Size(copy = true) annotation
    private static MethodValue createSizeMethodCheckForSizeCopyTrue(EvaluationResult.Builder builder,
                                                                    Location location,
                                                                    MethodAnalysis sizeMethodAnalysis,
                                                                    Value objectValue,
                                                                    EvaluationContext evaluationContext) {
        MethodValue methodValue;
        Primitives primitives = evaluationContext.getPrimitives();
        if ((methodValue = objectValue.asInstanceOf(MethodValue.class)) != null) {
            MethodAnalysis methodAnalysis = evaluationContext.getMethodAnalysis(methodValue.methodInfo);
            if (methodAnalysis.getProperty(VariableProperty.SIZE_COPY) == Level.SIZE_COPY_TRUE) {
                // there must be a sizeMethod()
                TypeInfo typeInfo = methodValue.object.type().bestTypeInfo();
                if (typeInfo == null)
                    throw new UnsupportedOperationException("Haze a @Size(copy = true) but the object type is not known?");
                MethodInfo sizeMethodInfoOnObject = typeInfo.sizeMethod(evaluationContext.getAnalyserContext());
                if (sizeMethodInfoOnObject == null)
                    throw new UnsupportedOperationException("Have a @Size(copy = true) but the object type has no size() method?");
                return new MethodValue(primitives, sizeMethodInfoOnObject, methodValue.object, List.of(),
                        sizeObjectFlow(evaluationContext, builder, location, sizeMethodAnalysis, objectValue));
            }
        }
        return new MethodValue(primitives, sizeMethodAnalysis.getMethodInfo(), objectValue, List.of(),
                sizeObjectFlow(evaluationContext, builder, location, sizeMethodAnalysis, objectValue));
    }

    private static ObjectFlow sizeObjectFlow(EvaluationContext evaluationContext,
                                             EvaluationResult.Builder builder,
                                             Location location,
                                             MethodAnalysis sizeMethodAnalysis,
                                             Value object) {
        if (object.getObjectFlow() != ObjectFlow.NO_FLOW) {
            builder.addAccess(false, new MethodAccess(sizeMethodAnalysis.getMethodInfo(), List.of()), object);
            ParameterizedType intParameterizedType = evaluationContext.getPrimitives().intParameterizedType;
            ObjectFlow source = sizeMethodAnalysis.getObjectFlow();
            ObjectFlow resultOfMethod = builder.createInternalObjectFlow(location, intParameterizedType, Origin.RESULT_OF_METHOD);
            resultOfMethod.addPrevious(source);
            return resultOfMethod;
        }
        return ObjectFlow.NO_FLOW;
    }

    private static Value computeSizeModifyingMethod(EvaluationResult.Builder builder,
                                                    MethodInfo methodInfo,
                                                    MethodAnalysis methodAnalysis,
                                                    Stream<ParameterAnalysis> parameterAnalyses,
                                                    Value objectValue, EvaluationContext evaluationContext) {
        int sizeOfMethod = methodAnalysis.getProperty(VariableProperty.SIZE);
        List<Integer> sizeInParameters = parameterAnalyses
                .map(p -> p.getProperty(VariableProperty.SIZE_COPY))
                .filter(v -> v > Level.FALSE)
                .collect(Collectors.toList());
        int numSizeInParameters = sizeInParameters.size();
        boolean haveSizeOnMethod = sizeOfMethod > Level.FALSE;
        if (numSizeInParameters == 0 && !haveSizeOnMethod) return null;
        if (numSizeInParameters > 1) {
            builder.raiseError(Message.MULTIPLE_SIZE_ANNOTATIONS);
            return null;
        }
        int newSize = numSizeInParameters == 1 ? sizeInParameters.get(0) : sizeOfMethod;
        int currentSize = evaluationContext.getProperty(objectValue, VariableProperty.SIZE);
        VariableValue variableValue;
        if (newSize > currentSize && (variableValue = objectValue.asInstanceOf(VariableValue.class)) != null) {
            builder.markSizeRestriction(variableValue.variable, newSize);
            log(SIZE, "Upgrade @Size of {} to {} because of method call {}", variableValue.variable.fullyQualifiedName(), newSize,
                    methodInfo.distinguishingName());
        } else {
            log(SIZE, "No effect of @Size annotation on modifying method call {}", methodInfo.distinguishingName());
        }
        return null;
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
