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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.annotation.NotNull;

import java.util.Set;
import java.util.stream.Stream;

/**
 * Defaults because of tests
 */
public interface EvaluationContext {

    default int getIteration() {
        return 0;
    }

    @NotNull
    default TypeAnalyser getCurrentType() {
        return null;
    }

    default MethodAnalyser getCurrentMethod() {
        return null;
    }

    default MethodAnalysis getCurrentMethodAnalysis() {
        return null;
    }

    default FieldAnalyser getCurrentField() {
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

    // on top of the normal condition and state in the current statement, we can add decisions from the ?: operator
    default EvaluationContext child(Value condition, Runnable uponUsingConditional, boolean guaranteedToBeReachedByParentStatement) {
        throw new UnsupportedOperationException();
    }

    default Value currentValue(Variable variable) {
        return UnknownValue.NO_VALUE;
    }

    default AnalyserContext getAnalyserContext() {
        return null;
    }

    default MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = getAnalyserContext().getMethodAnalysers().get(methodInfo);
        return methodAnalyser != null ? methodAnalyser.methodAnalysis : methodInfo.methodAnalysis.get();
    }

    default FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
        FieldAnalyser fieldAnalyser = getAnalyserContext().getFieldAnalysers().get(fieldInfo);
        return fieldAnalyser != null ? fieldAnalyser.fieldAnalysis : fieldInfo.fieldAnalysis.get();
    }

    default Stream<ParameterAnalysis> getParameterAnalyses(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = getAnalyserContext().getMethodAnalysers().get(methodInfo);
        return methodAnalyser != null ? methodAnalyser.getParameterAnalysers().stream().map(ParameterAnalyser::getParameterAnalysis)
                : methodInfo.methodInspection.get().parameters.stream().map(parameterInfo -> parameterInfo.parameterAnalysis.get());
    }

    default ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
        return getAnalyserContext().getParameterAnalysers().get(parameterInfo).parameterAnalysis;
    }

    default TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        TypeAnalyser typeAnalyser = getAnalyserContext().getTypeAnalysers().get(typeInfo);
        return typeAnalyser != null ? typeAnalyser.typeAnalysis : typeInfo.typeAnalysis.get();
    }

    default Stream<ObjectFlow> getInternalObjectFlows() {
        return Stream.empty();
    }

    default ObjectFlow getObjectFlow(Variable variable) {
        return currentValue(variable).getObjectFlow();
    }

    default int getProperty(Value value, VariableProperty variableProperty) {
        return value.getPropertyOutsideContext(variableProperty);
    }

    default Value currentValue(String variableName) {
        return UnknownValue.NO_VALUE;
    }

    default int getProperty(Variable variable, VariableProperty variableProperty) {
        return currentValue(variable).getPropertyOutsideContext(variableProperty);
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

    default String logLocation() {
        return getLocation().toString();
    }

    // Replacer
    default Set<String> allUnqualifiedVariableNames() {
        return Set.of();
    }

    default ConditionManager getConditionManager() {
        return null;
    }

}
