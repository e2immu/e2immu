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
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.abstractvalue.UnknownValue;
import org.e2immu.analyser.model.abstractvalue.VariableValue;
import org.e2immu.analyser.model.expression.ArrayAccess;
import org.e2immu.analyser.objectflow.Access;
import org.e2immu.analyser.objectflow.Location;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.objectflow.Origin;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.annotation.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * As soon as a change is registered in the EvaluationResult.Builder, and it is to be used further down
 * the evaluation chain, it should be visible here.
 */
public interface EvaluationContext {

    int getIteration();

    @NotNull
    TypeAnalyser getCurrentType();

    default MethodAnalyser getCurrentMethod() {
        return null;
    }

    Map<MethodInfo, MethodAnalyser> getMethodAnalysers();

    Map<FieldInfo, FieldAnalyser> getFieldAnalysers();

    Map<TypeInfo, TypeAnalyser> getTypeAnalysers();

    MethodAnalysis getCurrentMethodAnalysis();

    TypeAnalysis getCurrentPrimaryTypeAnalysis();


    default FieldAnalyser getCurrentField() {
        return null;
    }

    default StatementAnalyser getCurrentStatement() {
        return null;
    }

    default Location getLocation() {
        return getCurrentStatement() != null ? new Location(getCurrentMethod().methodInfo, getCurrentStatement().numberedStatement) :
                getCurrentMethod() != null ? new Location(getCurrentMethod().methodInfo) :
                        getCurrentField() != null ? new Location(getCurrentField().fieldInfo)
                                : new Location(getCurrentType().typeInfo);
    }

    // on top of the normal condition and state in the current statement, we can add decisions from the ?: operator
    EvaluationContext child(Value condition, Object o, boolean b);

    int getProperty(Value objectValue, VariableProperty size);

    Value currentValue(Variable variable);

    default MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = getMethodAnalysers().get(methodInfo);
        return methodAnalyser != null ? methodAnalyser.methodAnalysis : methodInfo.methodAnalysis.get();
    }

    default FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
        FieldAnalyser fieldAnalyser = getFieldAnalysers().get(fieldInfo);
        return fieldAnalyser != null ? fieldAnalyser.fieldAnalysis : fieldInfo.fieldAnalysis.get();
    }

    default Stream<ParameterAnalysis> getParameterAnalyses(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = getMethodAnalysers().get(methodInfo);
        return methodAnalyser != null ? methodAnalyser.getParameterAnalysers().stream().map(ParameterAnalyser::getParameterAnalysis)
                : methodInfo.methodInspection.get().parameters.stream().map(parameterInfo -> parameterInfo.parameterAnalysis.get());
    }

    default TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        TypeAnalyser typeAnalyser = getTypeAnalysers(typeInfo);
        return typeAnalyser != null ? typeAnalyser.typeAnalysis : typeInfo.typeAnalysis.get();
    }

    // constant stuff, needed for Lambda to create a new method analyser

    Configuration getConfiguration();

    PatternMatcher getPatternMatcher();

    E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions();
}
