/*
 * e2immu: a static code analyser for effective and eventual immutability
 * Copyright 2020-2021, Bart Naudts, https://www.e2immu.org
 *
 * This program is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Lesser General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License for
 * more details. You should have received a copy of the GNU Lesser General Public
 * License along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analysis.*;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.ImportantClasses;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;

import java.util.stream.Stream;

/**
 * many default values are there to make testing easier
 */
public interface AnalyserContext extends AnalysisProvider, InspectionProvider {

    default Configuration getConfiguration() {
        return new Configuration.Builder().build();
    }

    // gives access to primitives
    Primitives getPrimitives();

    default ImportantClasses importantClasses() {
        throw new UnsupportedOperationException();
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

    default Stream<MethodAnalyser> methodAnalyserStream() {
        throw new UnsupportedOperationException();
    }

    default MethodAnalyser getMethodAnalyser(MethodInfo methodInfo) {
        throw new UnsupportedOperationException();
    }

    default FieldAnalyser getFieldAnalyser(FieldInfo fieldInfo) {
        throw new UnsupportedOperationException();
    }

    default Stream<FieldAnalyser> fieldAnalyserStream() {
        throw new UnsupportedOperationException();
    }

    default TypeAnalyser getTypeAnalyser(TypeInfo typeInfo) {
        throw new UnsupportedOperationException();
    }

    default AnalyserContext getParent() {
        return null;
    }

    default FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
        return fieldInfo.fieldAnalysis.get(fieldInfo.fullyQualifiedName);
    }

    default ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
        return parameterInfo.parameterAnalysis.get(parameterInfo.fullyQualifiedName);
    }

    default ParameterAnalysis getParameterAnalysisNullWhenAbsent(ParameterInfo parameterInfo) {
        return parameterInfo.parameterAnalysis.getOrDefaultNull();
    }

    default TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        return typeInfo.typeAnalysis.get(typeInfo.fullyQualifiedName);
    }

    default TypeAnalysis getTypeAnalysisNullWhenAbsent(TypeInfo typeInfo) {
        return typeInfo.typeAnalysis.getOrDefaultNull();
    }

    default MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        return methodInfo.methodAnalysis.get(methodInfo.fullyQualifiedName);
    }

    default MethodAnalysis getMethodAnalysisNullWhenAbsent(MethodInfo methodInfo) {
        return methodInfo.methodAnalysis.getOrDefaultNull();
    }

    default FieldInspection getFieldInspection(FieldInfo fieldInfo) {
        return fieldInfo.fieldInspection.get(fieldInfo.fullyQualifiedName);
    }

    default TypeInspection getTypeInspection(TypeInfo typeInfo) {
        return typeInfo.typeInspection.get(typeInfo.fullyQualifiedName);
    }

    default MethodInspection getMethodInspection(MethodInfo methodInfo) {
        return methodInfo.methodInspection.get(methodInfo.fullyQualifiedName);
    }

   default   boolean isStore() { return false; }

   default void store(Analysis analysis) { throw new UnsupportedOperationException(); }
}
