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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.util.EvaluateParameters;
import org.e2immu.analyser.model.expression.util.ExpressionComparator;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.output.OutputBuilder;
import org.e2immu.analyser.output.Space;
import org.e2immu.analyser.output.Symbol;
import org.e2immu.analyser.output.Text;
import org.e2immu.analyser.parser.Primitives;
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

    public NewObject(NewObject newObject, Expression newState) {
        this(newObject.constructor, newObject.parameterizedType, newObject.parameterExpressions,
                newObject.anonymousClass, newObject.arrayInitializer, newState, newObject.objectFlow);
    }

    //for testing
    public NewObject(Primitives primitives, ParameterizedType parameterizedType) {
        this(null, parameterizedType, List.of(), null, null,
                new BooleanConstant(primitives, true), ObjectFlow.NO_FLOW);
    }

    public NewObject(Primitives primitives, ParameterizedType parameterizedType, ObjectFlow objectFlow) {
        this(null, parameterizedType, List.of(), null, null,
                new BooleanConstant(primitives, true), objectFlow);
    }

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
        this(constructor, parameterizedType, parameterExpressions, null, arrayInitializer, state, objectFlow);
    }

    // constructor can be null, when we create an anonymous class that doesn't derive from a class with constructor
    // in that case, there is a default, parameterless constructor
    public NewObject(Primitives primitives, @NotNull ParameterizedType parameterizedType, @NotNull TypeInfo anonymousClass) {
        this(null, parameterizedType, List.of(), anonymousClass, null, new BooleanConstant(primitives, true),
                ObjectFlow.NO_FLOW);
    }

    private NewObject(MethodInfo constructor,
                      ParameterizedType parameterizedType,
                      List<Expression> parameterExpressions,
                      TypeInfo anonymousClass,
                      ArrayInitializer arrayInitializer,
                      Expression state,
                      ObjectFlow objectFlow) {
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.parameterExpressions = Objects.requireNonNull(parameterExpressions);
        this.constructor = constructor; // can be null after modification (constructor lost)
        this.anonymousClass = anonymousClass;
        this.arrayInitializer = arrayInitializer;
        this.state = Objects.requireNonNull(state);
        this.objectFlow = Objects.requireNonNull(objectFlow);
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
        return minimalOutput();
    }

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
    public LinkedVariables linkedVariables(EvaluationContext evaluationContext) {
        // RULE 1
        if (parameterExpressions == null || constructor == null) return LinkedVariables.EMPTY;
        if (parameterExpressions.isEmpty() && constructor.typeInfo.isStatic()) {
            return LinkedVariables.EMPTY;
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
                return LinkedVariables.EMPTY;
            }
            if (independent == Level.DELAY) return null;
            if (immutable == MultiLevel.DELAY) return null;
            if (typeIndependent == MultiLevel.DELAY) return null;
        }

        // default case
        Set<Variable> result = new HashSet<>();
        for (Expression value : parameterExpressions) {
            LinkedVariables sub = evaluationContext.linkedVariables(value);
            if (sub == LinkedVariables.DELAY) return LinkedVariables.DELAY;
            result.addAll(sub.variables());
        }
        return new LinkedVariables(result);
    }

    @Override
    public int internalCompareTo(Expression v) {
        return parameterizedType.detailedString()
                .compareTo(((NewObject) v).parameterizedType.detailedString());
    }

    @Override
    public NewObject getInstance(EvaluationResult evaluationContext) {
        return this;
    }

    @Override
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        switch (variableProperty) {
            case NOT_NULL: {
                TypeInfo bestType = parameterizedType.bestTypeInfo();
                if (Primitives.isPrimitiveExcludingVoid(bestType)) return MultiLevel.EFFECTIVELY_NOT_NULL;
                if (constructor != null) {
                    // if the constructor is there, it is really a case of "new X(...)", which is never null
                    return bestType == null ? MultiLevel.EFFECTIVELY_NOT_NULL :
                            MultiLevel.bestNotNull(MultiLevel.EFFECTIVELY_NOT_NULL,
                                    evaluationContext.getTypeAnalysis(bestType).getProperty(VariableProperty.NOT_NULL));
                }
                // otherwise, we're simply looking at "an" instance which may or may not exist
                return MultiLevel.NULLABLE;
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
                if (Primitives.isPrimitiveExcludingVoid(bestType)) return variableProperty.best;
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
    public boolean isNumeric() {
        return parameterizedType.isType() && Primitives.isNumeric(parameterizedType.typeInfo);
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
    public OutputBuilder output() {
        OutputBuilder outputBuilder = new OutputBuilder();
        if (constructor != null) {
            outputBuilder.add(new Text("new")).add(Space.ONE).add(parameterizedType.output());
            if (!returnType().parameters.isEmpty()) {
                outputBuilder.add(Symbol.DIAMOND); // TODO there are situations where diamond is not good enough
            }
            if (arrayInitializer == null) {
                if (parameterExpressions.isEmpty()) {
                    outputBuilder.add(Symbol.OPEN_CLOSE_PARENTHESIS);
                } else {
                    outputBuilder
                            .add(Symbol.LEFT_PARENTHESIS)
                            .add(parameterExpressions.stream().map(Expression::output).collect(OutputBuilder.joining(Symbol.COMMA)))
                            .add(Symbol.RIGHT_PARENTHESIS);
                }
            }
        } else {
            outputBuilder.add(new Text("instance type")).add(Space.ONE).add(parameterizedType.output());
        }
        if (anonymousClass != null) {
            outputBuilder.add(anonymousClass.output());
        }
        if (arrayInitializer != null) {
            outputBuilder.add(arrayInitializer.output());
        }
        if (!state.isBoolValueTrue()) {
            outputBuilder.add(Symbol.LEFT_BLOCK_COMMENT).add(state.output()).add(Symbol.RIGHT_BLOCK_COMMENT);
        }
        return outputBuilder;
    }

    @Override
    public Precedence precedence() {
        return Precedence.UNARY;
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
                new BooleanConstant(evaluationContext.getPrimitives(), true), objectFlow);
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
