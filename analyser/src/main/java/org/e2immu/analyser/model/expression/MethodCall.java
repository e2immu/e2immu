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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.e2immu.analyser.analyser.StatementAnalyser;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.abstractvalue.*;
import org.e2immu.analyser.model.value.BoolValue;
import org.e2immu.analyser.model.value.IntValue;
import org.e2immu.analyser.model.value.NullValue;
import org.e2immu.analyser.model.value.StringValue;
import org.e2immu.analyser.objectflow.Location;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.parser.SideEffectContext;
import org.e2immu.annotation.NotNull;

import java.util.*;
import java.util.stream.Collectors;

import static org.e2immu.analyser.util.Logger.LogTarget.SIZE;
import static org.e2immu.analyser.util.Logger.log;

public class MethodCall extends ExpressionWithMethodReferenceResolution implements HasParameterExpressions {
    public final Expression object;
    public final Expression computedScope;
    public final List<Expression> parameterExpressions;

    public MethodCall(@NotNull Expression object,
                      @NotNull Expression computedScope,
                      @NotNull MethodTypeParameterMap methodTypeParameterMap,
                      @NotNull List<Expression> parameterExpressions) {
        super(methodTypeParameterMap.methodInfo, methodTypeParameterMap.getConcreteReturnType());
        this.object = object;
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        this.computedScope = Objects.requireNonNull(computedScope);
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

        // not modified on scope
        SideEffect sideEffect = methodInfo.sideEffect();
        boolean safeMethod = sideEffect.lessThan(SideEffect.SIDE_EFFECT);
        int modifiedValue = sideEffect == SideEffect.DELAYED ? Level.DELAY : safeMethod ? Level.FALSE : Level.TRUE;
        int methodDelay = Level.fromBool(sideEffect == SideEffect.DELAYED);

        // scope
        Value objectValue = computedScope.evaluate(evaluationContext, visitor, new ForwardEvaluationInfo(Map.of(
                VariableProperty.NOT_NULL, Level.TRUE,
                VariableProperty.METHOD_CALLED, Level.TRUE,
                VariableProperty.METHOD_DELAY, methodDelay,
                VariableProperty.MODIFIED, modifiedValue), false));


        // null scope
        if (objectValue instanceof NullValue) {
            evaluationContext.raiseError(Message.NULL_POINTER_EXCEPTION);
        }

        // process parameters
        List<Value> parameters = NewObject.transform(parameterExpressions, evaluationContext, visitor, methodInfo);

        // access
        ObjectFlow objectFlow = objectValue.getObjectFlow();
        if (objectFlow != ObjectFlow.NO_FLOW) {
            List<ObjectFlow> flowsOfArguments = parameters.stream().map(Value::getObjectFlow).collect(Collectors.toList());
            objectFlow.addObjectAccess(new ObjectFlow.MethodCall(methodInfo, flowsOfArguments));
        }

        // return value
        ObjectFlow objectFlowOfResult;
        if (!methodInfo.returnType().isVoid()) {
            ObjectFlow.MethodCalls origin = new ObjectFlow.MethodCalls();
            ObjectFlow returnedFlow = methodInfo.methodAnalysis.get().getReturnedObjectFlow();
            Location location = evaluationContext.getLocation();
            objectFlowOfResult = new ObjectFlow(location, methodInfo.returnType(), origin);

            // cross-link
            if (returnedFlow != ObjectFlow.NO_FLOW) {
                origin.objectFlows.add(returnedFlow);
                returnedFlow.addReturnOrFieldAccessFlow(objectFlowOfResult);
            }
            location.registerNewObjectFlow(objectFlowOfResult);
        } else {
            objectFlowOfResult = ObjectFlow.NO_FLOW;
        }
        Value result = methodValue(evaluationContext, methodInfo, objectValue, parameters, objectFlowOfResult);

        checkForwardRequirements(methodInfo.methodAnalysis.get(), forwardEvaluationInfo, evaluationContext);
        visitor.visit(this, evaluationContext, result);

        checkCommonErrors(evaluationContext, objectValue, parameters);

        return result;
    }

    private void checkCommonErrors(EvaluationContext evaluationContext, Value objectValue, List<Value> parameters) {
        if (methodInfo.fullyQualifiedName().equals("java.lang.String.toString()")) {
            ParameterizedType type = objectValue.type();
            if (type != null && type.typeInfo != null && type.typeInfo == Primitives.PRIMITIVES.stringTypeInfo) {
                evaluationContext.raiseError(Message.UNNECESSARY_METHOD_CALL);
            }
        }
    }

    public static Value methodValue(EvaluationContext evaluationContext, MethodInfo methodInfo, Value objectValue, List<Value> parameters, ObjectFlow objectFlowOfResult) {
        // no value (method call on field that does not have effective value yet)
        if (objectValue == UnknownValue.NO_VALUE) {
            return UnknownValue.NO_VALUE; // this will delay
        }

        // eval on constant, like "abc".length()
        Value evaluationOnConstant = computeEvaluationOnConstant(methodInfo, objectValue, parameters);
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
        Value identity = computeIdentity(methodAnalysis, parameters);
        if (identity != null) {
            return identity;
        }

        // @Fluent as method annotation
        Value fluent = computeFluent(methodAnalysis, objectValue);
        if (fluent != null) {
            return fluent;
        }

        if (methodAnalysis.singleReturnValue.isSet()) {
            // if this method was identity?
            Value srv = methodAnalysis.singleReturnValue.get();
            if (srv instanceof InlineValue) {
                return srv.reEvaluate(NewObject.translationMap(evaluationContext, methodInfo, parameters));
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

    private static Value computeEvaluationOnConstant(MethodInfo methodInfo, Value objectValue, List<Value> parameters) {
        if (!objectValue.isConstant()) return null;
        if ("java.lang.String.length()".equals(methodInfo.fullyQualifiedName()) && objectValue instanceof StringValue) {
            return new IntValue(((StringValue) objectValue).value.length());
        }
        return null;
    }

    private void checkForwardRequirements(MethodAnalysis methodAnalysis, ForwardEvaluationInfo forwardEvaluationInfo, EvaluationContext evaluationContext) {
        // compliance with current requirements
        int requiredNotNull = forwardEvaluationInfo.getProperty(VariableProperty.NOT_NULL);
        if (requiredNotNull >= Level.TRUE) {
            int currentNotNull = methodAnalysis.getProperty(VariableProperty.NOT_NULL);
            if (currentNotNull == Level.FALSE) {
                evaluationContext.raiseError(Message.POTENTIAL_NULL_POINTER_EXCEPTION, "Result of method call " + methodInfo.distinguishingName());
            }
        }
        // TODO not modified requirements on result of method call? may be tricky
        int requiredSize = forwardEvaluationInfo.getProperty(VariableProperty.SIZE);
        if (requiredSize > Level.FALSE) {
            int currentSize = methodAnalysis.getProperty(VariableProperty.SIZE);
            if (!Analysis.compatibleSizes(currentSize, requiredSize)) {
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

    private static Value wrap(MethodAnalysis methodAnalysis, Value value) {
        Map<VariableProperty, Integer> map = new HashMap<>();
        for (VariableProperty property : VariableProperty.PROPERTIES_IN_METHOD_RESULT_WRAPPER) {
            int v = methodAnalysis.getProperty(property);
            if (v != Level.DELAY) map.put(property, v);
        }
        return PropertyWrapper.propertyWrapper(value, map);
    }

    private static Value computeIdentity(MethodAnalysis methodAnalysis, List<Value> parameters) {
        int identity = methodAnalysis.getProperty(VariableProperty.IDENTITY);
        if (identity == Level.DELAY && methodAnalysis.hasBeenDefined) return UnknownValue.NO_VALUE; // delay
        if (identity != Level.TRUE) return null;
        return wrap(methodAnalysis, parameters.get(0));
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
        if (requiredSize <= Analysis.NOT_A_SIZE) return null;
        // we have an @Size annotation on the method that we're calling
        int sizeOfObject = evaluationContext != null ? evaluationContext.getProperty(objectValue, VariableProperty.SIZE) :
                objectValue.getPropertyOutsideContext(VariableProperty.SIZE);

        // SITUATION 1: @Size(equals = 0) boolean isEmpty() { }, @Size(min = 1) boolean isNotEmpty() {}, etc.

        // TODO causes null pointer exception
        Location location = evaluationContext == null ? objectValue.getObjectFlow().location : evaluationContext.getLocation();
        if (methodInfo.returnType().isBoolean()) {
            // there is an @Size annotation on a method returning a boolean...
            if (sizeOfObject <= Analysis.IS_A_SIZE) {
                log(SIZE, "Required @Size is {}, but we have no information. Result could be true or false.");
                return sizeMethodValue(methodInfo, objectValue, requiredSize, location);
            }
            // we have a requirement, and we have a size.
            if (Analysis.haveEquals(requiredSize)) {
                // we require a fixed size.
                if (Analysis.haveEquals(sizeOfObject)) {
                    if (evaluationContext != null) evaluationContext.raiseError(Message.METHOD_EVALUATES_TO_CONSTANT);
                    log(SIZE, "Required @Size is {}, and we have {}. Result is a constant true or false.", requiredSize, sizeOfObject);
                    return sizeOfObject == requiredSize ? BoolValue.TRUE : BoolValue.FALSE;
                }
                if (sizeOfObject > requiredSize) {
                    log(SIZE, "Required @Size is {}, and we have {}. Result is always false.", requiredSize, sizeOfObject);
                    if (evaluationContext != null) evaluationContext.raiseError(Message.METHOD_EVALUATES_TO_CONSTANT);
                    return BoolValue.FALSE;
                }
                log(SIZE, "Required @Size is {}, and we have {}. Result could be true or false.", requiredSize, sizeOfObject);
                return sizeMethodValue(methodInfo, objectValue, requiredSize, location); // we want =3, but we have >= 2; could be anything
            }
            if (sizeOfObject > requiredSize) {
                log(SIZE, "Required @Size is {}, and we have {}. Result is always true.", requiredSize, sizeOfObject);
                if (evaluationContext != null) evaluationContext.raiseError(Message.METHOD_EVALUATES_TO_CONSTANT);
                return BoolValue.TRUE;
            }
            log(SIZE, "Required @Size is {}, and we have {}. Result could be true or false.", requiredSize, sizeOfObject);
            return sizeMethodValue(methodInfo, objectValue, requiredSize, location);
        }

        // SITUATION 2: @Size int size(): this method returns the size
        if (TypeInfo.returnsIntOrLong(methodInfo)) {
            if (Analysis.haveEquals(sizeOfObject)) {
                return new IntValue(Analysis.decodeSizeEquals(sizeOfObject));
            }
            ObjectFlow objectFlow = sizeObjectFlow(methodInfo, location, objectValue);
            return ConstrainedNumericValue.lowerBound(new MethodValue(methodInfo, objectValue, parameters, objectFlow),
                    Analysis.decodeSizeMin(sizeOfObject));
        }
        return null;
    }

    // instead of returning isEmpty(), we return size() == 0
    // instead of returning isNotEmpty(), we return size() >= 1
    private static Value sizeMethodValue(MethodInfo methodInfo, Value objectValue, int requiredSize, Location location) {
        MethodInfo sizeMethodInfo = methodInfo.typeInfo.sizeMethod();
        MethodValue sizeMethod = createSizeMethodCheckForSizeCopyTrue(sizeMethodInfo, objectValue, location);
        if (Analysis.haveEquals(requiredSize)) {
            ConstrainedNumericValue constrainedSizeMethod = ConstrainedNumericValue.lowerBound(sizeMethod, 0);
            ObjectFlow objectFlow = new ObjectFlow(location, Primitives.PRIMITIVES.booleanParameterizedType, new ObjectFlow.MethodCalls());
            return EqualsValue.equals(new IntValue(Analysis.decodeSizeEquals(requiredSize),
                            new ObjectFlow(location, Primitives.PRIMITIVES.intParameterizedType, new ObjectFlow.MethodCalls())),
                    constrainedSizeMethod, objectFlow);
        }
        return ConstrainedNumericValue.lowerBound(sizeMethod, Analysis.decodeSizeMin(requiredSize));
    }

    // we normally return  objectValue.size();
    // but if objectValue is a method itself, and that method preserves the size, then we remove that method
    // object.entrySet().size() == object.size(); the entrySet() method has a @Size(copy = true) annotation
    private static MethodValue createSizeMethodCheckForSizeCopyTrue(MethodInfo sizeMethodInfo, Value objectValue, Location location) {
        if (objectValue instanceof MethodValue) {
            MethodValue methodValue = (MethodValue) objectValue;
            if (methodValue.methodInfo.methodAnalysis.get().getProperty(VariableProperty.SIZE_COPY) == Level.TRUE_LEVEL_1) {
                // there must be a sizeMethod()
                TypeInfo typeInfo = methodValue.object.type().bestTypeInfo();
                if (typeInfo == null)
                    throw new UnsupportedOperationException("Haze a @Size(copy = true) but the object type is not known?");
                MethodInfo sizeMethodInfoOnObject = typeInfo.sizeMethod();
                if (sizeMethodInfoOnObject == null)
                    throw new UnsupportedOperationException("Have a @Size(copy = true) but the object type has no size() method?");
                return new MethodValue(sizeMethodInfoOnObject, methodValue.object, List.of(), sizeObjectFlow(sizeMethodInfo, location, objectValue));
            }
        }
        return new MethodValue(sizeMethodInfo, objectValue, List.of(), sizeObjectFlow(sizeMethodInfo, location, objectValue));
    }

    private static ObjectFlow sizeObjectFlow(MethodInfo sizeMethodInfo, Location location, Value object) {
        ObjectFlow.MethodCalls methodCalls = new ObjectFlow.MethodCalls();
        ObjectFlow objectFlow = new ObjectFlow(location, Primitives.PRIMITIVES.intParameterizedType, methodCalls);
        if (object.getObjectFlow() != ObjectFlow.NO_FLOW) {
            object.getObjectFlow().addObjectAccess(new ObjectFlow.MethodCall(sizeMethodInfo, List.of()));
            methodCalls.objectFlows.add(object.getObjectFlow());
        }
        return objectFlow;
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
        if (newSize > currentSize && objectValue instanceof VariableValue) {
            Variable variable = ((VariableValue) objectValue).variable;
            evaluationContext.addProperty(variable, VariableProperty.SIZE, newSize);
            log(SIZE, "Upgrade @Size of {} to {} because of method call {}", variable.detailedString(), newSize,
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
    public Set<String> imports() {
        Set<String> imports = new HashSet<>(object == null ? Set.of() : object.imports());
        parameterExpressions.forEach(pe -> imports.addAll(pe.imports()));
        return ImmutableSet.copyOf(imports);
    }

    @Override
    public List<Expression> subExpressions() {
        List<Expression> list = new ArrayList<>(parameterExpressions);
        if (object != null) {
            list.add(object);
        }
        return ImmutableList.copyOf(list);
    }

    @Override
    public List<Variable> variablesInScopeSide() {
        return object == null ? List.of() : object.variables();
    }

    @Override
    public SideEffect sideEffect(SideEffectContext sideEffectContext) {
        Objects.requireNonNull(sideEffectContext);

        SideEffect params = parameterExpressions.stream()
                .map(e -> e.sideEffect(sideEffectContext))
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
            SideEffect sideEffect = object.sideEffect(sideEffectContext);
            if (sideEffect == SideEffect.STATIC_ONLY && params.lessThan(SideEffect.SIDE_EFFECT)) {
                return SideEffect.STATIC_ONLY;
            }
        }

        SideEffect methodsSideEffect = methodInfo.sideEffect();
        if (methodsSideEffect == SideEffect.STATIC_ONLY && params.lessThan(SideEffect.SIDE_EFFECT)) {
            return SideEffect.STATIC_ONLY;
        }
        return methodsSideEffect.combine(params);
    }
}
