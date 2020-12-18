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

import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.pattern.PatternMatcher;

import java.util.Map;

/**
 * many default values are there to make testing easier
 */
public interface AnalyserContext extends AnalysisProvider, InspectionProvider {

    default Configuration getConfiguration() {
        return new Configuration.Builder().build();
    }

    // gives access to primitives
    default Primitives getPrimitives() {
        return new Primitives();
    }

    /**
     * Used by ConditionalValue, isFact().
     *
     * @return true when analysing annotated API files (companion methods)
     */
    default boolean inAnnotatedAPIAnalysis() {
        return false;
    }

    default E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions() {
        return null;
    }

    default PatternMatcher<StatementAnalyser> getPatternMatcher() {
        return null;
    }

    default TypeInfo getPrimaryType() {
        return null;
    }

    default Map<MethodInfo, MethodAnalyser> getMethodAnalysers() {
        return null;
    }

    default Map<FieldInfo, FieldAnalyser> getFieldAnalysers() {
        return null;
    }

    default Map<TypeInfo, TypeAnalyser> getTypeAnalysers() {
        return null;
    }

    default Map<ParameterInfo, ParameterAnalyser> getParameterAnalysers() {
        return null;
    }

    default FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
        try {
            FieldAnalyser fieldAnalyser = getFieldAnalysers().get(fieldInfo);
            if (fieldAnalyser == null) return fieldInfo.fieldAnalysis.get();
            return fieldAnalyser.fieldAnalysis;
        } catch (UnsupportedOperationException e) {
            throw new UnsupportedOperationException("Field analysis of " + fieldInfo.fullyQualifiedName() + " not yet set");
        }
    }

    default ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
        ParameterAnalyser parameterAnalyser = getParameterAnalysers().get(parameterInfo);
        if (parameterAnalyser == null) {
            if (parameterInfo.parameterAnalysis.isSet()) return parameterInfo.parameterAnalysis.get();
            throw new UnsupportedOperationException("Parameter analysis of " + parameterInfo.fullyQualifiedName() + " not yet set");
        }
        return parameterAnalyser.parameterAnalysis;
    }

    default TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        try {
            TypeAnalyser typeAnalyser = getTypeAnalysers().get(typeInfo);
            if (typeAnalyser == null) return typeInfo.typeAnalysis.get();
            return typeAnalyser.typeAnalysis;
        } catch (UnsupportedOperationException e) {
            throw new UnsupportedOperationException("Type analysis of " + typeInfo.fullyQualifiedName + " not yet set");
        }
    }

    default MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        try {
            MethodAnalyser methodAnalyser = getMethodAnalysers().get(methodInfo);
            if (methodAnalyser == null) return methodInfo.methodAnalysis.get();
            return methodAnalyser.methodAnalysis;
        } catch (UnsupportedOperationException e) {
            throw new UnsupportedOperationException("Method analysis of " + methodInfo.fullyQualifiedName() + " not yet set");
        }
    }

    default FieldInspection getFieldInspection(FieldInfo fieldInfo) {
        return fieldInfo.fieldInspection.get();
    }

    default TypeInspection getTypeInspection(TypeInfo typeInfo) {
        return typeInfo.typeInspection.get();
    }

    default MethodInspection getMethodInspection(MethodInfo methodInfo) {
        return methodInfo.methodInspection.get();
    }

}
