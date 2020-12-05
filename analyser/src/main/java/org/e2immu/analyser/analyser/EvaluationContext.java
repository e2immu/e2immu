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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.EmptyExpression;
import org.e2immu.analyser.model.expression.NewObject;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.model.variable.Variable;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.annotation.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Defaults because of tests
 */
public interface EvaluationContext {
    Logger LOGGER = LoggerFactory.getLogger(EvaluationContext.class);

    default int getIteration() {
        return 0;
    }

    @NotNull
    default TypeInfo getCurrentType() {
        return null;
    }

    default MethodAnalyser getCurrentMethod() {
        return null;
    }

    default MethodAnalysis getCurrentMethodAnalysis() {
        return null;
    }

    default StatementAnalyser getCurrentStatement() {
        return null;
    }

    default Location getLocation() {
        return null;
    }

    default Location getLocation(Expression expression) {
        return null;
    }

    default Primitives getPrimitives() {
        return getAnalyserContext().getPrimitives();
    }

    // on top of the normal condition and state in the current statement, we can add decisions from the ?: operator
    default EvaluationContext child(Expression condition) {
        throw new UnsupportedOperationException();
    }

    default EvaluationContext child(Expression condition, boolean disableEvaluationOfMethodCallsUsingCompanionMethods) {
        return child(condition);
    }

    default Expression currentValue(Variable variable) {
        return EmptyExpression.NO_VALUE;
    }

    default AnalyserContext getAnalyserContext() {
        throw new UnsupportedOperationException();
    }

    default MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalysis methodAnalysis = getAnalyserContext().getMethodAnalysis(methodInfo);
        return methodAnalysis != null ? methodAnalysis : methodInfo.methodAnalysis.get();
    }

    default FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
        FieldAnalyser fieldAnalyser = getAnalyserContext().getFieldAnalysers().get(fieldInfo);
        return fieldAnalyser != null ? fieldAnalyser.fieldAnalysis : fieldInfo.fieldAnalysis.get();
    }

    default Stream<ParameterAnalysis> getParameterAnalyses(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = getAnalyserContext().getMethodAnalysers().get(methodInfo);
        return methodAnalyser != null ? methodAnalyser.getParameterAnalysers().stream().map(ParameterAnalyser::getParameterAnalysis)
                : methodInfo.methodInspection.get().getParameters().stream().map(parameterInfo -> parameterInfo.parameterAnalysis.get());
    }

    default ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
        ParameterAnalysis parameterAnalysis = getAnalyserContext().getParameterAnalysis(parameterInfo);
        return parameterAnalysis != null ? parameterAnalysis : parameterInfo.parameterAnalysis.get();
    }

    default TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        try {
            TypeAnalyser typeAnalyser = getAnalyserContext().getTypeAnalysers().get(typeInfo);
            return typeAnalyser != null ? typeAnalyser.typeAnalysis : typeInfo.typeAnalysis.get();
        } catch (RuntimeException e) {
            LOGGER.error("Caught exception in default get type analysis: " + typeInfo.fullyQualifiedName);
            throw e;
        }
    }

    default Stream<ObjectFlow> getInternalObjectFlows() {
        return Stream.empty();
    }

    default ObjectFlow getObjectFlow(Variable variable) {
        return currentValue(variable).getObjectFlow();
    }

    default int getProperty(Expression value, VariableProperty variableProperty) {
        if (value instanceof VariableExpression variableValue) {
            Variable variable = variableValue.variable();
            if (variable instanceof ParameterInfo parameterInfo) {
                return getParameterAnalysis(parameterInfo).getProperty(variableProperty);
            }
            if (variable instanceof FieldReference fieldReference) {
                return getFieldAnalysis(fieldReference.fieldInfo).getProperty(variableProperty);
            }
            if (variable instanceof This thisVariable) {
                return getTypeAnalysis(thisVariable.typeInfo).getProperty(variableProperty);
            }
            if (variable instanceof PreAspectVariable pre) {
                return pre.valueForProperties().getProperty(this, variableProperty);
            }
            throw new UnsupportedOperationException("Variable value of type " + variable.getClass());
        }
        return value.getProperty(this, variableProperty); // will work in many cases
    }

    default int getProperty(Variable variable, VariableProperty variableProperty) {
        throw new UnsupportedOperationException();
    }

    default int summarizeModification(Set<Variable> linkedVariables) {
        boolean hasDelays = false;
        for (Variable variable : linkedVariables) {
            int modified = getProperty(variable, VariableProperty.MODIFIED);
            int methodDelay = getProperty(variable, VariableProperty.METHOD_DELAY);
            if (modified == Level.TRUE) return Level.TRUE;
            if (methodDelay == Level.TRUE) hasDelays = true;
        }
        return hasDelays ? Level.DELAY : Level.FALSE;
    }

    // Replacer
    default Set<String> allUnqualifiedVariableNames() {
        return Set.of();
    }

    default ConditionManager getConditionManager() {
        return null;
    }

    default boolean isNotNull0(Expression value) {
        return true;
    }

    default Set<Variable> linkedVariables(Expression value) {
        return value.linkedVariables(this);
    }

    default Set<Variable> linkedVariables(Variable variable) {
        return Set.of();
    }

    default Map<VariableProperty, Integer> getValueProperties(Expression value) {
        return VariableProperty.VALUE_PROPERTIES.stream().collect(Collectors.toMap(vp -> vp, vp -> getProperty(value, vp)));
    }

    /*
    This default implementation is the correct one for basic tests and the companion analyser (we cannot use companions in the
    companion analyser, that would be chicken-and-egg).
     */
    default NewObject currentInstance(Variable variable) {
        if (Primitives.isPrimitiveExcludingVoid(variable.parameterizedType())) return null;
        // always a new one with empty state -- we cannot be bothered here.
        return new NewObject(null, variable.parameterizedType(), List.of(),
                EmptyExpression.EMPTY_EXPRESSION, ObjectFlow.NO_FLOW);
    }

    default boolean disableEvaluationOfMethodCallsUsingCompanionMethods() {
        return getAnalyserContext().inAnnotatedAPIAnalysis();
    }
}
