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

import org.e2immu.analyser.analyser.StatementAnalyser;
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
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.Only;

import java.util.*;
import java.util.stream.Collectors;

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
    public List<Expression> getParameterExpressions() {
        return parameterExpressions;
    }

    @Override
    public MethodInfo getMethodInfo() {
        return methodInfo;
    }

    @Override
    public Value evaluate(EvaluationContext evaluationContext, EvaluationVisitor visitor, ForwardEvaluationInfo forwardEvaluationInfo) {
        StatementAnalyser.checkForIllegalMethodUsageIntoNestedOrEnclosingType(methodInfo, evaluationContext);

        // is the method modifying, do we need to wait?
        int modified = methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
        int methodDelay = Level.fromBool(modified == Level.DELAY);

        // effectively not null is the default, but when we're in a not null situation, we can demand effectively content not null
        int notNullForward = notNullRequirementOnScope(forwardEvaluationInfo.getProperty(VariableProperty.NOT_NULL));
        boolean contentNotNullRequired = notNullForward == MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL;

        // scope
        Value objectValue = computedScope.evaluate(evaluationContext, visitor, new ForwardEvaluationInfo(Map.of(
                VariableProperty.NOT_NULL, notNullForward,
                VariableProperty.METHOD_CALLED, Level.TRUE,
                VariableProperty.METHOD_DELAY, methodDelay,
                VariableProperty.MODIFIED, modified), true));


        // null scope
        if (objectValue.isInstanceOf(NullValue.class)) {
            evaluationContext.raiseError(Message.NULL_POINTER_EXCEPTION);
        }

        // process parameters
        int notModified1Scope = objectValue.getProperty(evaluationContext, VariableProperty.NOT_MODIFIED_1);
        List<Value> parameterValues = EvaluateParameters.transform(parameterExpressions, evaluationContext, visitor, methodInfo, notModified1Scope);

        // access
        ObjectFlow objectFlow = objectValue.getObjectFlow();
        if (objectFlow != ObjectFlow.NO_FLOW) {
            if (modified == Level.DELAY) {
                Logger.log(DELAYED, "Delaying flow access registration");
                objectFlow.delay();
            } else {
                List<ObjectFlow> flowsOfArguments = parameterValues.stream().map(Value::getObjectFlow).collect(Collectors.toList());
                MethodAccess methodAccess = new MethodAccess(methodInfo, flowsOfArguments);
                evaluationContext.addAccess(modified == Level.TRUE, methodAccess, objectValue);
            }
        }
        ValueWithVariable valueWithVariable;
        if (modified == Level.TRUE && (valueWithVariable = objectValue.asInstanceOf(ValueWithVariable.class)) != null) {
            evaluationContext.modifyingMethodAccess(valueWithVariable.variable);
        }

        // @Only check
        checkOnly(objectFlow, evaluationContext);

        // return value
        ObjectFlow objectFlowOfResult;
        if (!methodInfo.returnType().isVoid()) {
            ObjectFlow returnedFlow = methodInfo.methodAnalysis.get().getObjectFlow();

            objectFlowOfResult = evaluationContext.createInternalObjectFlow(methodInfo.returnType(), Origin.RESULT_OF_METHOD);
            objectFlowOfResult.addPrevious(returnedFlow);
            // cross-link, possible because returnedFlow is already permanent
            // TODO ObjectFlow check cross-link
            returnedFlow.addNext(objectFlowOfResult);
        } else {
            objectFlowOfResult = ObjectFlow.NO_FLOW;
        }

        Value result;
        if (!methodInfo.isVoid()) {
            complianceWithForwardRequirements(methodInfo.methodAnalysis.get(), forwardEvaluationInfo, evaluationContext, contentNotNullRequired);

            result = methodValue(evaluationContext, methodInfo, objectValue, parameterValues, objectFlowOfResult);
        } else {
            result = UnknownValue.NO_RETURN_VALUE;
        }

        visitor.visit(this, evaluationContext, result);

        checkCommonErrors(evaluationContext, objectValue);

        return result;
    }

    private int notNullRequirementOnScope(int notNullRequirement) {
        if (methodInfo.typeInfo.isFunctionalInterface() && MultiLevel.isEffectivelyNotNull(notNullRequirement)) {
            return MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL; // @NotNull1
        }
        return MultiLevel.EFFECTIVELY_NOT_NULL;
    }

    private void checkOnly(ObjectFlow objectFlow, EvaluationContext evaluationContext) {
        Optional<AnnotationExpression> oOnly = methodInfo.methodInspection.get().annotations.stream()
                .filter(ae -> ae.typeInfo.fullyQualifiedName.equals(Only.class.getName())).findFirst();
        if (oOnly.isPresent()) {
            AnnotationExpression ae = oOnly.get();
            String before = ae.extract("before", "");
            if (!before.isEmpty()) {
                Set<String> marks = objectFlow.marks();
                if (marks.contains(before)) {
                    evaluationContext.raiseError(Message.ONLY_BEFORE, methodInfo.fullyQualifiedName() +
                            ", mark \"" + before + "\"");
                }
            } else {
                String after = ae.extract("after", "");
                Set<String> marks = objectFlow.marks();
                if (!marks.contains(after)) {
                    evaluationContext.raiseError(Message.ONLY_AFTER, methodInfo.fullyQualifiedName() +
                            ", mark \"" + after + "\"");
                }
            }
        }
    }

    private void checkCommonErrors(EvaluationContext evaluationContext, Value objectValue) {
        if (methodInfo.fullyQualifiedName().equals("java.lang.String.toString()")) {
            ParameterizedType type = objectValue.type();
            if (type != null && type.typeInfo != null && type.typeInfo == Primitives.PRIMITIVES.stringTypeInfo) {
                evaluationContext.raiseError(Message.UNNECESSARY_METHOD_CALL);
            }
        }

        MethodInfo method;
        if (objectValue instanceof InlineValue) {
            method = ((InlineValue) objectValue).methodInfo;
        } else {
            method = methodInfo;
        }
        int modified = method.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
        int immutable = objectValue.getProperty(evaluationContext, VariableProperty.IMMUTABLE);
        if (modified == Level.TRUE && immutable >= MultiLevel.EVENTUALLY_E2IMMUTABLE) {
            evaluationContext.raiseError(Message.CALLING_MODIFYING_METHOD_ON_E2IMMU,
                    "Method: " + methodInfo.distinguishingName() + ", Type: " + objectValue.type());
        }
    }

    public static Value methodValue(EvaluationContext evaluationContext, MethodInfo methodInfo, Value objectValue, List<Value> parameters, ObjectFlow objectFlowOfResult) {
        Objects.requireNonNull(evaluationContext);

        // no value (method call on field that does not have effective value yet)
        if (objectValue == UnknownValue.NO_VALUE) {
            return UnknownValue.NO_VALUE; // this will delay
        }

        // eval on constant, like "abc".length()
        Value evaluationOnConstant = computeEvaluationOnConstant(methodInfo, objectValue);
        if (evaluationOnConstant != null) {
            return evaluationOnConstant;
        }

        // @Size as method annotation
        Value sizeShortCut = computeSize(methodInfo, objectValue, parameters, evaluationContext);
        if (sizeShortCut != null) {
            return sizeShortCut;
        }

        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();

        // @Identity as method annotation
        Value identity = computeIdentity(methodAnalysis, parameters, objectFlowOfResult);
        if (identity != null) {
            return identity;
        }

        // @Fluent as method annotation
        Value fluent = computeFluent(methodAnalysis, objectValue);
        if (fluent != null) {
            return fluent;
        }

        InlineValue inlineValue;
        if (methodInfo.typeInfo.isFunctionalInterface() && (inlineValue = objectValue.asInstanceOf(InlineValue.class)) != null &&
                inlineValue.canBeApplied(evaluationContext)) {
            Map<Value, Value> translationMap = EvaluateParameters.translationMap(evaluationContext, methodInfo, parameters);
            return inlineValue.reEvaluate(evaluationContext, translationMap);
        }

        if (methodAnalysis.singleReturnValue.isSet()) {
            // if this method was identity?
            Value srv = methodAnalysis.singleReturnValue.get();
            if (srv.isInstanceOf(InlineValue.class)) {
                Map<Value, Value> translationMap = EvaluateParameters.translationMap(evaluationContext, methodInfo, parameters);
                return srv.reEvaluate(evaluationContext, translationMap);
            }
            if (srv.isConstant()) {
                return srv;
            }
        } else if (methodInfo.hasBeenDefined()) {
            // we will, at some point, analyse this method
            return UnknownValue.NO_VALUE;
        }

        // normal method value
        return new MethodValue(methodInfo, objectValue, parameters, objectFlowOfResult);
    }

    private static Value computeEvaluationOnConstant(MethodInfo methodInfo, Value objectValue) {
        if (!objectValue.isConstant()) return null;
        StringValue stringValue;
        if ("java.lang.String.length()".equals(methodInfo.fullyQualifiedName()) &&
                (stringValue = objectValue.asInstanceOf(StringValue.class)) != null) {
            return new IntValue(stringValue.value.length());
        }
        return null;
    }

    private void complianceWithForwardRequirements(MethodAnalysis methodAnalysis,
                                                   ForwardEvaluationInfo forwardEvaluationInfo,
                                                   EvaluationContext evaluationContext,
                                                   boolean contentNotNullRequired) {
        if (!contentNotNullRequired) {
            int requiredNotNull = forwardEvaluationInfo.getProperty(VariableProperty.NOT_NULL);
            if (MultiLevel.isEffectivelyNotNull(requiredNotNull)) {
                boolean isNotNull = MultiLevel.isEffectivelyNotNull(methodAnalysis.getProperty(VariableProperty.NOT_NULL));
                if (!isNotNull) {
                    evaluationContext.raiseError(Message.POTENTIAL_NULL_POINTER_EXCEPTION,
                            "Result of method call " + methodInfo.distinguishingName());
                }
            }
        } // else: we've already requested this from the scope (functional interface)

        // TODO @NotModified1

        int requiredSize = forwardEvaluationInfo.getProperty(VariableProperty.SIZE);
        if (requiredSize > Level.FALSE) {
            int currentSize = methodAnalysis.getProperty(VariableProperty.SIZE);
            if (!Level.compatibleSizes(currentSize, requiredSize)) {
                evaluationContext.raiseError(Message.POTENTIAL_SIZE_PROBLEM);
            }
        }
    }

    private static Value computeFluent(MethodAnalysis methodAnalysis, Value scope) {
        int fluent = methodAnalysis.getProperty(VariableProperty.FLUENT);
        if (fluent == Level.DELAY && methodAnalysis.hasBeenDefined) return UnknownValue.NO_VALUE;
        if (fluent != Level.TRUE) return null;
        return scope;
    }


    private static Value computeIdentity(MethodAnalysis methodAnalysis, List<Value> parameters, ObjectFlow objectFlowOfResult) {
        int identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
        if (identity == Level.DELAY && methodAnalysis.hasBeenDefined) return UnknownValue.NO_VALUE; // delay
        if (identity != Level.TRUE) return null;

        Map<VariableProperty, Integer> map = new HashMap<>();
        for (VariableProperty property : VariableProperty.PROPERTIES_IN_METHOD_RESULT_WRAPPER) {
            int v = methodAnalysis.getProperty(property);
            if (v != Level.DELAY) map.put(property, v);
        }
        return PropertyWrapper.propertyWrapper(parameters.get(0), map, objectFlowOfResult);
    }

    private static Value computeSize(MethodInfo methodInfo, Value objectValue, List<Value> parameters, EvaluationContext evaluationContext) {
        // if (!computedScope.returnType().hasSize()) return null; // this type does not do size computations
        if (!methodInfo.typeInfo.hasSize()) return null;

        int modified = methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
        if (modified == Level.DELAY) {
            return null; // ignore
        }
        if (modified == Level.TRUE) {
            return computeSizeModifyingMethod(methodInfo, objectValue, evaluationContext);
        }

        int requiredSize = methodInfo.methodAnalysis.get().getProperty(VariableProperty.SIZE);
        if (requiredSize <= Level.NOT_A_SIZE) return null;
        // we have an @Size annotation on the method that we're calling
        int sizeOfObject = evaluationContext.getProperty(objectValue, VariableProperty.SIZE);

        // SITUATION 1: @Size(equals = 0) boolean isEmpty() { }, @Size(min = 1) boolean isNotEmpty() {}, etc.

        // TODO causes null pointer exception
        if (methodInfo.returnType().isBoolean()) {
            // there is an @Size annotation on a method returning a boolean...
            if (sizeOfObject <= Level.IS_A_SIZE) {
                log(SIZE, "Required @Size is {}, but we have no information. Result could be true or false.");
                return sizeMethodValue(methodInfo, objectValue, requiredSize, evaluationContext);
            }
            // we have a requirement, and we have a size.
            if (Level.haveEquals(requiredSize)) {
                // we require a fixed size.
                if (Level.haveEquals(sizeOfObject)) {
                    evaluationContext.raiseError(Message.METHOD_EVALUATES_TO_CONSTANT);
                    log(SIZE, "Required @Size is {}, and we have {}. Result is a constant true or false.", requiredSize, sizeOfObject);
                    return sizeOfObject == requiredSize ? BoolValue.TRUE : BoolValue.FALSE;
                }
                if (sizeOfObject > requiredSize) {
                    log(SIZE, "Required @Size is {}, and we have {}. Result is always false.", requiredSize, sizeOfObject);
                    evaluationContext.raiseError(Message.METHOD_EVALUATES_TO_CONSTANT);
                    return BoolValue.FALSE;
                }
                log(SIZE, "Required @Size is {}, and we have {}. Result could be true or false.", requiredSize, sizeOfObject);
                return sizeMethodValue(methodInfo, objectValue, requiredSize, evaluationContext); // we want =3, but we have >= 2; could be anything
            }
            if (sizeOfObject > requiredSize) {
                log(SIZE, "Required @Size is {}, and we have {}. Result is always true.", requiredSize, sizeOfObject);
                evaluationContext.raiseError(Message.METHOD_EVALUATES_TO_CONSTANT);
                return BoolValue.TRUE;
            }
            log(SIZE, "Required @Size is {}, and we have {}. Result could be true or false.", requiredSize, sizeOfObject);
            return sizeMethodValue(methodInfo, objectValue, requiredSize, evaluationContext);
        }

        // SITUATION 2: @Size int size(): this method returns the size
        if (TypeInfo.returnsIntOrLong(methodInfo)) {
            if (Level.haveEquals(sizeOfObject)) {
                return new IntValue(Level.decodeSizeEquals(sizeOfObject));
            }
            ObjectFlow objectFlow = sizeObjectFlow(methodInfo, evaluationContext, objectValue);
            return ConstrainedNumericValue.lowerBound(new MethodValue(methodInfo, objectValue, parameters, objectFlow),
                    Level.decodeSizeMin(sizeOfObject));
        }
        return null;
    }

    // instead of returning isEmpty(), we return size() == 0
    // instead of returning isNotEmpty(), we return size() >= 1
    private static Value sizeMethodValue(MethodInfo methodInfo, Value objectValue, int requiredSize, EvaluationContext evaluationContext) {
        MethodInfo sizeMethodInfo = methodInfo.typeInfo.sizeMethod();
        MethodValue sizeMethod = createSizeMethodCheckForSizeCopyTrue(sizeMethodInfo, objectValue, evaluationContext);
        if (Level.haveEquals(requiredSize)) {
            ConstrainedNumericValue constrainedSizeMethod = ConstrainedNumericValue.lowerBound(sizeMethod, 0);
            ObjectFlow objectFlow = evaluationContext.createInternalObjectFlow(Primitives.PRIMITIVES.booleanParameterizedType, Origin.RESULT_OF_METHOD);
            return EqualsValue.equals(new IntValue(Level.decodeSizeEquals(requiredSize),
                            evaluationContext.createInternalObjectFlow(Primitives.PRIMITIVES.intParameterizedType, Origin.RESULT_OF_METHOD)),
                    constrainedSizeMethod, objectFlow);
        }
        return ConstrainedNumericValue.lowerBound(sizeMethod, Level.decodeSizeMin(requiredSize));
    }

    // we normally return  objectValue.size();
    // but if objectValue is a method itself, and that method preserves the size, then we remove that method
    // object.entrySet().size() == object.size(); the entrySet() method has a @Size(copy = true) annotation
    private static MethodValue createSizeMethodCheckForSizeCopyTrue(MethodInfo sizeMethodInfo, Value objectValue, EvaluationContext evaluationContext) {
        MethodValue methodValue;
        if ((methodValue = objectValue.asInstanceOf(MethodValue.class)) != null) {
            if (methodValue.methodInfo.methodAnalysis.get().getProperty(VariableProperty.SIZE_COPY) == Level.SIZE_COPY_TRUE) {
                // there must be a sizeMethod()
                TypeInfo typeInfo = methodValue.object.type().bestTypeInfo();
                if (typeInfo == null)
                    throw new UnsupportedOperationException("Haze a @Size(copy = true) but the object type is not known?");
                MethodInfo sizeMethodInfoOnObject = typeInfo.sizeMethod();
                if (sizeMethodInfoOnObject == null)
                    throw new UnsupportedOperationException("Have a @Size(copy = true) but the object type has no size() method?");
                return new MethodValue(sizeMethodInfoOnObject, methodValue.object, List.of(), sizeObjectFlow(sizeMethodInfo, evaluationContext, objectValue));
            }
        }
        return new MethodValue(sizeMethodInfo, objectValue, List.of(), sizeObjectFlow(sizeMethodInfo, evaluationContext, objectValue));
    }

    private static ObjectFlow sizeObjectFlow(MethodInfo sizeMethodInfo, EvaluationContext evaluationContext, Value object) {
        if (object.getObjectFlow() != ObjectFlow.NO_FLOW) {
            evaluationContext.addAccess(false, new MethodAccess(sizeMethodInfo, List.of()), object);

            ObjectFlow source = sizeMethodInfo.methodAnalysis.get().getObjectFlow();
            ObjectFlow resultOfMethod = evaluationContext.createInternalObjectFlow(Primitives.PRIMITIVES.intParameterizedType, Origin.RESULT_OF_METHOD);
            resultOfMethod.addPrevious(source);
            return resultOfMethod;
        }
        return ObjectFlow.NO_FLOW;
    }

    private static Value computeSizeModifyingMethod(MethodInfo methodInfo, Value objectValue, EvaluationContext evaluationContext) {
        int sizeOfMethod = methodInfo.methodAnalysis.get().getProperty(VariableProperty.SIZE);
        List<Integer> sizeInParameters = methodInfo.methodInspection.get()
                .parameters.stream()
                .map(p -> p.parameterAnalysis.get().getProperty(VariableProperty.SIZE_COPY))
                .filter(v -> v > Level.FALSE)
                .collect(Collectors.toList());
        int numSizeInParameters = sizeInParameters.size();
        boolean haveSizeOnMethod = sizeOfMethod > Level.FALSE;
        if (numSizeInParameters == 0 && !haveSizeOnMethod) return null;
        if (numSizeInParameters > 1) {
            evaluationContext.raiseError(Message.MULTIPLE_SIZE_ANNOTATIONS);
            return null;
        }
        int newSize = numSizeInParameters == 1 ? sizeInParameters.get(0) : sizeOfMethod;
        int currentSize = evaluationContext.getProperty(objectValue, VariableProperty.SIZE);
        VariableValue variableValue;
        if (newSize > currentSize && (variableValue = objectValue.asInstanceOf(VariableValue.class)) != null) {
            evaluationContext.addProperty(variableValue.variable, VariableProperty.SIZE, newSize);
            log(SIZE, "Upgrade @Size of {} to {} because of method call {}", variableValue.variable.detailedString(), newSize,
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
        return ListUtil.immutableConcat(parameterExpressions, object == null ? List.of() : List.of(object));
    }

    @Override
    public List<Variable> variablesInScopeSide() {
        return object == null ? List.of() : object.variables();
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        Objects.requireNonNull(evaluationContext);

        SideEffect params = parameterExpressions.stream()
                .map(e -> e.sideEffect(evaluationContext))
                .reduce(SideEffect.LOCAL, SideEffect::combine);

        // look at the object... if it is static, we're in the same boat
        if (object instanceof FieldAccess) {
            FieldAccess fieldAccess = (FieldAccess) object;
            if (fieldAccess.variable.isStatic() && params.lessThan(SideEffect.SIDE_EFFECT))
                return SideEffect.STATIC_ONLY;
        }
        if (object instanceof VariableExpression) {
            VariableExpression variableExpression = (VariableExpression) object;
            if (variableExpression.variable.isStatic() && params.lessThan(SideEffect.SIDE_EFFECT))
                return SideEffect.STATIC_ONLY;
        }
        if (object != null) {
            SideEffect sideEffect = object.sideEffect(evaluationContext);
            if (sideEffect == SideEffect.STATIC_ONLY && params.lessThan(SideEffect.SIDE_EFFECT)) {
                return SideEffect.STATIC_ONLY;
            }
        }

        SideEffect methodsSideEffect = sideEffectNotTakingEventualIntoAccount(methodInfo);
        if (methodsSideEffect == SideEffect.STATIC_ONLY && params.lessThan(SideEffect.SIDE_EFFECT)) {
            return SideEffect.STATIC_ONLY;
        }
        return methodsSideEffect.combine(params);
    }

    private static SideEffect sideEffectNotTakingEventualIntoAccount(MethodInfo methodInfo) {
        int modified = methodInfo.methodAnalysis.get().getProperty(VariableProperty.MODIFIED);
        int immutable = methodInfo.typeInfo.typeAnalysis.get().getProperty(VariableProperty.IMMUTABLE);
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
