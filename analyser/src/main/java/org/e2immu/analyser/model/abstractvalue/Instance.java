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

package org.e2immu.analyser.model.abstractvalue;

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.output.PrintMode;
import org.e2immu.annotation.NotNull;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * the result of a new object creation (new XX(...))
 */

public class Instance implements Value {
    @NotNull
    public final ParameterizedType parameterizedType;
    @NotNull
    public final List<Value> constructorParameterValues;
    public final MethodInfo constructor;
    public final ObjectFlow objectFlow;

    public Instance(@NotNull ParameterizedType parameterizedType, MethodInfo constructor, List<Value> parameterValues, ObjectFlow objectFlow) {
        this.parameterizedType = Objects.requireNonNull(parameterizedType);
        this.constructor = constructor; // con be null, in anonymous classes
        this.constructorParameterValues = ImmutableList.copyOf(parameterValues);
        this.objectFlow = objectFlow;
    }

    // every new instance is different.
    // we may come back to this later, but for now...
    @Override
    public boolean equals(Object o) {
        return this == o;
    }

    @Override
    public int order() {
        return ORDER_INSTANCE;
    }

    @Override
    public int internalCompareTo(Value v) {
        return parameterizedType.detailedString()
                .compareTo(((Instance) v).parameterizedType.detailedString());
    }

    @Override
    public String toString() {
        return print(PrintMode.FOR_DEBUG);
    }

    @Override
    public String print(PrintMode printMode) {
        return "instance type " + parameterizedType.detailedString()
                + "(" + constructorParameterValues.stream()
                .map(Value::toString)
                .collect(Collectors.joining(", ")) + ")";
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
        if (constructorParameterValues == null || constructor == null) return NO_LINKS;
        if (constructorParameterValues.isEmpty() && constructor.typeInfo.isStatic()) {
            return NO_LINKS;
        }

        // RULE 2, 3
        boolean notSelf = constructor.typeInfo != evaluationContext.getCurrentType().typeInfo;
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
        for (Value value : constructorParameterValues) {
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
    public int getProperty(EvaluationContext evaluationContext, VariableProperty variableProperty) {
        switch (variableProperty) {
            case NOT_NULL: {
                TypeInfo bestType = parameterizedType.bestTypeInfo();
                return bestType == null ? MultiLevel.EFFECTIVELY_NOT_NULL :
                        MultiLevel.bestNotNull(MultiLevel.EFFECTIVELY_NOT_NULL,
                                evaluationContext.getTypeAnalysis(bestType).getProperty(VariableProperty.NOT_NULL));
            }
            case SIZE:
                return MethodValue.checkSize(evaluationContext, constructor, constructorParameterValues);

            case SIZE_COPY:
                return MethodValue.checkSizeCopy(evaluationContext, constructor);

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
    public boolean hasConstantProperties() {
        return false;
    }

    @Override
    public ObjectFlow getObjectFlow() {
        return objectFlow;
    }

    @Override
    public void visit(Consumer<Value> consumer) {
        constructorParameterValues.forEach(v -> v.visit(consumer));
        consumer.accept(this);
    }
}
