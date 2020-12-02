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

import org.e2immu.analyser.analyser.EvaluationContext;
import org.e2immu.analyser.analyser.EvaluationResult;
import org.e2immu.analyser.analyser.ForwardEvaluationInfo;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.EvaluateParameters;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.analyser.util.Pair;
import org.e2immu.analyser.util.UpgradableBooleanMap;
import org.e2immu.annotation.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/*
 Represents first a newly constructed object, then after applying modifying methods, a "used" object

 */
public class NewObject implements HasParameterExpressions {
    public final MethodInfo constructor;   // ... = new HashSet<>()
    public final ParameterizedType parameterizedType; // HashSet<String>
    public final List<Expression> parameterExpressions; // = new HashSet<>(strings)
    public final TypeInfo anonymousClass;  // ... = new Function<String, String>() { ... }
    public final ArrayInitializer arrayInitializer; // int[] a = {1, 2, 3}
    public final Expression state;  // ... information about the object from companion methods
    public final ObjectFlow objectFlow; // generally a new flow

    public NewObject(MethodInfo constructor,
                     ParameterizedType parameterizedType,
                     List<Expression> parameterExpressions,
                     Expression state,
                     ObjectFlow objectFlow) {
        this(constructor, parameterizedType, parameterExpressions, null, state, objectFlow);
    }

    public NewObject(MethodInfo constructor,
                     ParameterizedType parameterizedType,
                     List<Expression> parameterExpressions,
                     ArrayInitializer arrayInitializer,
                     Expression state,
                     ObjectFlow objectFlow) {
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        this.constructor = constructor; // can be null after modification (constructor lost)
        this.anonymousClass = null;
        this.arrayInitializer = arrayInitializer;
        this.state = Objects.requireNonNull(state);
        this.objectFlow = Objects.requireNonNull(objectFlow);
    }

    // constructor can be null, when we create an anonymous class that doesn't derive from a class with constructor
    // in that case, there is a default, parameterless constructor
    public NewObject(@NotNull ParameterizedType parameterizedType, @NotNull TypeInfo anonymousClass) {
        this.anonymousClass = Objects.requireNonNull(anonymousClass);
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.parameterExpressions = List.of();
        this.constructor = null;
        this.arrayInitializer = null;
        this.state = EmptyExpression.EMPTY_EXPRESSION;
        this.objectFlow = ObjectFlow.NO_FLOW; // TODO
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NewObject newObject = (NewObject) o;
        return parameterizedType.equals(newObject.parameterizedType) &&
                parameterExpressions.equals(newObject.parameterExpressions) &&
                Objects.equals(anonymousClass, newObject.anonymousClass) &&
                Objects.equals(constructor, newObject.constructor) &&
                Objects.equals(arrayInitializer, newObject.arrayInitializer);
    }

    @Override
    public int hashCode() {
        return Objects.hash(parameterizedType, parameterExpressions, anonymousClass, constructor, arrayInitializer);
    }

    @Override
    public Expression translate(TranslationMap translationMap) {
        return new NewObject(constructor,
                translationMap.translateType(parameterizedType),
                parameterExpressions.stream().map(translationMap::translateExpression).collect(Collectors.toList()),
                TranslationMap.ensureExpressionType(arrayInitializer, ArrayInitializer.class),
                state, objectFlow);
    }

    @Override
    public int order() {
        return ExpressionComparator.ORDER_INSTANCE;
    }


    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return "instance type " + parameterizedType.detailedString()
                + (constructor == null ? "" : (
                "(" + parameterExpressions.stream()
                        .map(Expression::toString)
                        .collect(Collectors.joining(", ")) + ")"))
                + (state == EmptyExpression.EMPTY_EXPRESSION ? "" : "[" + state.print(printMode) + "]");
    }

    private static final Set<Variable> NO_LINKS = Set.of();

    /*
     * Rules, assuming the notation b = new B(c, d)
     *
     * 1. no explicit constructor, no parameters on a static type: independent
     * 2. constructor is @Independent: independent
     * 3. B is @E2Immutable: independent
     *
     * the default case is a dependence on c and d
     */
    @Override
    public Set<Variable> linkedVariables(EvaluationContext evaluationContext) {
        // RULE 1
        if (parameterExpressions == null || constructor == null) return NO_LINKS;
        if (parameterExpressions.isEmpty() && constructor.typeInfo.isStatic()) {
            return NO_LINKS;
        }

        // RULE 2, 3
        boolean notSelf = constructor.typeInfo != evaluationContext.getCurrentType();
        if (notSelf) {
            TypeAnalysis typeAnalysisOfConstructor = evaluationContext.getTypeAnalysis(constructor.typeInfo);
            int immutable = typeAnalysisOfConstructor.getProperty(VariableProperty.IMMUTABLE);
            int typeIndependent = typeAnalysisOfConstructor.getProperty(VariableProperty.INDEPENDENT);
            MethodAnalysis methodAnalysisOfConstructor = evaluationContext.getMethodAnalysis(constructor);
            int independent = methodAnalysisOfConstructor.getProperty(VariableProperty.INDEPENDENT);

            if (MultiLevel.isE2Immutable(immutable) || independent == MultiLevel.EFFECTIVE
                    || typeIndependent == MultiLevel.EFFECTIVE) { // RULE 3
                return NO_LINKS;
            }
            if (independent == Level.DELAY) return null;
            if (immutable == MultiLevel.DELAY) return null;
            if (typeIndependent == MultiLevel.DELAY) return null;
        }

        // default case
        Set<Variable> result = new HashSet<>();
        for (Expression value : parameterExpressions) {
            Set<Variable> sub = evaluationContext.linkedVariables(value);
            if (sub == null) return null; // DELAY
            result.addAll(sub);
        }
        return result;
    }

    @Override
    public ParameterizedType type() {
        return parameterizedType;
    }


    @Override
    public int internalCompareTo(Expression v) {
        return parameterizedType.detailedString()
                .compareTo(((NewObject) v).parameterizedType.detailedString());
    }

    @Override
    public NewObject getInstance(EvaluationContext evaluationContext) {
        return this;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        switch (variableProperty) {
            case NOT_NULL: {
                TypeInfo bestType = parameterizedType.bestTypeInfo();
                return bestType == null ? MultiLevel.EFFECTIVELY_NOT_NULL :
                        MultiLevel.bestNotNull(MultiLevel.EFFECTIVELY_NOT_NULL,
                                evaluationContext.getTypeAnalysis(bestType).getProperty(VariableProperty.NOT_NULL));
            }
            case MODIFIED:
            case NOT_MODIFIED_1:
            case METHOD_DELAY:
            case IDENTITY:
            case IGNORE_MODIFICATIONS:
                return Level.FALSE;

            case IMMUTABLE:
            case CONTAINER: {
                TypeInfo bestType = parameterizedType.bestTypeInfo();
                return bestType == null ? variableProperty.falseValue :
                        Math.max(variableProperty.falseValue,
                                evaluationContext.getTypeAnalysis(bestType).getProperty(variableProperty));
            }
            default:
        }
        // @NotModified should not be asked here
        throw new UnsupportedOperationException("Asking for " + variableProperty);
    }

    @Override
    public void visit(Predicate<Expression> predicate) {
        if (predicate.test(this)) {
            parameterExpressions.forEach(predicate::test);
        }
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public MethodInfo getMethodInfo() {
        return constructor;
    }

    @Override
    public List<Expression> getParameterExpressions() {
        return parameterExpressions;
    }

    @Override
    public ParameterizedType returnType() {
        return parameterizedType;
    }

    @Override
    public String expressionString(int indent) {
        String expressionString;
        if (parameterizedType.arrays > 0) {
            expressionString = parameterExpressions.stream().map(expression -> "[" + expression.expressionString(indent) + "]")
                    .collect(Collectors.joining(", "));
        } else {
            expressionString = "(" +
                    parameterExpressions.stream().map(expression -> expression.expressionString(indent)).collect(Collectors.joining(", ")) +
                    ")";
        }
        String anon = (anonymousClass == null ? "" : anonymousClass.stream(indent, false)).stripTrailing();
        String arrayInit = arrayInitializer == null ? "" : arrayInitializer.expressionString(0);
        return "new " + parameterizedType.streamWithoutArrays() + expressionString + anon + arrayInit;
    }

    @Override
    public int precedence() {
        return 13;
    }

    @Override
    public UpgradableBooleanMap<TypeInfo> typesReferenced() {
        return UpgradableBooleanMap.of(
                parameterizedType.typesReferenced(true),
                parameterExpressions.stream().flatMap(e -> e.typesReferenced().stream()).collect(UpgradableBooleanMap.collector()));
    }

    @Override
    public List<? extends Element> subElements() {
        return parameterExpressions;
    }

    @Override
    public EvaluationResult evaluate(EvaluationContext evaluationContext, ForwardEvaluationInfo forwardEvaluationInfo) {

        // arrayInitializer variant

        if (arrayInitializer != null) {
            EvaluationResult.Builder builder = new EvaluationResult.Builder(evaluationContext);
            List<EvaluationResult> results = arrayInitializer.multiExpression.stream()
                    .map(e -> e.evaluate(evaluationContext, ForwardEvaluationInfo.DEFAULT))
                    .collect(Collectors.toList());
            builder.compose(results);
            List<Expression> values = results.stream().map(EvaluationResult::getExpression).collect(Collectors.toList());
            ObjectFlow objectFlow = builder.createLiteralObjectFlow(arrayInitializer.returnType());
            builder.setExpression(new ArrayInitializer(evaluationContext.getPrimitives(), objectFlow, values));
            return builder.build();
        }

        // "normal"

        Pair<EvaluationResult.Builder, List<Expression>> res = EvaluateParameters.transform(parameterExpressions,
                evaluationContext, constructor, Level.FALSE, null);
        Location location = evaluationContext.getLocation(this);
        ObjectFlow objectFlow = res.k.createInternalObjectFlow(location, parameterizedType, Origin.NEW_OBJECT_CREATION);

        NewObject initialInstance = new NewObject(constructor, parameterizedType, res.v,
                EmptyExpression.EMPTY_EXPRESSION, objectFlow);
        NewObject instance;
        if (constructor != null) {
            // check state changes of companion methods
            MethodAnalysis constructorAnalysis = evaluationContext.getMethodAnalysis(constructor);
            instance = MethodCall.checkCompanionMethodsModifying(res.k, evaluationContext, constructor, constructorAnalysis,
                    initialInstance, res.v);
        } else {
            instance = initialInstance;
        }
        res.k.setExpression(instance);
        return res.k.build();
    }

    @Override
    public SideEffect sideEffect(EvaluationContext evaluationContext) {
        SideEffect params = parameterExpressions.stream()
                .map(e -> e.sideEffect(evaluationContext))
                .reduce(SideEffect.LOCAL, SideEffect::combine);

        if (constructor != null) {
            int modified = constructor.atLeastOneParameterModified();
            if (modified == Level.FALSE && params.lessThan(SideEffect.SIDE_EFFECT)) {
                return SideEffect.STATIC_ONLY;
            }
            if (modified == Level.DELAY) return SideEffect.DELAYED;
        }

        return params;
    }
}
