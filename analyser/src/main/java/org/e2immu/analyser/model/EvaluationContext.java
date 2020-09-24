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
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.annotation.NotNull;

import java.util.stream.Stream;

/**
 * As soon as a change is registered in the EvaluationResult.Builder, and it is to be used further down
 * the evaluation chain, it should be visible here.
 */
public interface EvaluationContext {

    int getIteration();

    @NotNull
    TypeAnalyser getCurrentType();

    MethodAnalyser getCurrentMethod();

    MethodAnalysis getCurrentMethodAnalysis();

    FieldAnalyser getCurrentField();

    StatementAnalyser getCurrentStatement();

    Location getLocation();

    // on top of the normal condition and state in the current statement, we can add decisions from the ?: operator
    EvaluationContext child(Value condition, Runnable uponUsingConditional, boolean guaranteedToBeReachedByParentStatement);

    Value currentValue(Variable variable);

    AnalyserContext getAnalyserContext();

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

    default TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        TypeAnalyser typeAnalyser = getAnalyserContext().getTypeAnalysers().get(typeInfo);
        return typeAnalyser != null ? typeAnalyser.typeAnalysis : typeInfo.typeAnalysis.get();
    }

    Stream<ObjectFlow> getInternalObjectFlows();

    default ObjectFlow getObjectFlow(Variable variable) {
        return currentValue(variable).getObjectFlow();
    }

    default int getProperty(Value value, VariableProperty variableProperty) {
        return value.getPropertyOutsideContext(variableProperty);
    }

    Value currentValue(String variableName);

    default int getProperty(Variable variable, VariableProperty variableProperty) {
        return currentValue(variable).getPropertyOutsideContext(variableProperty);
    }
}
