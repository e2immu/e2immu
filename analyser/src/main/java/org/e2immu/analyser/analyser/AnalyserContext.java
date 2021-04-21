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

import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.model.variable.This;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.pattern.PatternMatcher;

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

    default Stream<MethodAnalyser> methodAnalyserStream() {
        return null;
    }

    default MethodAnalyser getMethodAnalyser(MethodInfo methodInfo) {
        return null;
    }

    default FieldAnalyser getFieldAnalyser(FieldInfo fieldInfo) {
        return null;
    }

    default Stream<FieldAnalyser> fieldAnalyserStream() {
        return null;
    }

    default TypeAnalyser getTypeAnalyser(TypeInfo typeInfo) {
        return null;
    }

    default ParameterAnalyser getParameterAnalyser(ParameterInfo parameterInfo) {
        return null;
    }

    default AnalyserContext getParent() {
        return null;
    }

    default FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
        FieldAnalyser fieldAnalyser = getFieldAnalyser(fieldInfo);
        if (fieldAnalyser == null) {
            AnalyserContext parent = getParent();
            if (parent != null) return parent.getFieldAnalysis(fieldInfo);
            return fieldInfo.fieldAnalysis.get(fieldInfo.fullyQualifiedName());
        }
        return fieldAnalyser.fieldAnalysis;
    }

    default ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
        ParameterAnalyser parameterAnalyser = getParameterAnalyser(parameterInfo);
        if (parameterAnalyser != null) {
            return parameterAnalyser.parameterAnalysis;
        }
        AnalyserContext parent = getParent();
        if (parent != null) return parent.getParameterAnalysis(parameterInfo);

        if (parameterInfo.parameterAnalysis.isSet()) return parameterInfo.parameterAnalysis.get();
        throw new UnsupportedOperationException("Parameter analysis of " + parameterInfo.fullyQualifiedName() + " not yet set");
    }

    default TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        TypeAnalyser typeAnalyser = getTypeAnalyser(typeInfo);
        if (typeAnalyser == null) {
            AnalyserContext parent = getParent();
            if (parent != null) return parent.getTypeAnalysis(typeInfo);
            return typeInfo.typeAnalysis.get(typeInfo.fullyQualifiedName);
        }
        return typeAnalyser.typeAnalysis;
    }

    default MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = getMethodAnalyser(methodInfo);
        if (methodAnalyser == null) {
            AnalyserContext parent = getParent();
            if (parent != null) return parent.getMethodAnalysis(methodInfo);
            return methodInfo.methodAnalysis.get(methodInfo.fullyQualifiedName);
        }
        return methodAnalyser.methodAnalysis;
    }

    default FieldInspection getFieldInspection(FieldInfo fieldInfo) {
        return fieldInfo.fieldInspection.get();
    }

    default TypeInspection getTypeInspection(TypeInfo typeInfo) {
        return typeInfo.typeInspection.get(typeInfo.fullyQualifiedName);
    }

    default MethodInspection getMethodInspection(MethodInfo methodInfo) {
        return methodInfo.methodInspection.get(methodInfo.fullyQualifiedName);
    }

    default FieldReference adjustThis(FieldReference fieldReference) {
        if (fieldReference.scope instanceof This thisVar && thisVar.typeInfo != fieldReference.fieldInfo.owner) {
            This newThis = new This(this, fieldReference.fieldInfo.owner);
            return new FieldReference(this, fieldReference.fieldInfo, newThis);
        }
        return fieldReference;
    }
}
