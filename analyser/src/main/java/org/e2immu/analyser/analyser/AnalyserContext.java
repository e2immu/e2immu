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

import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.config.AnalyserProgram;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.ImportantClasses;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.pattern.PatternMatcher;

import java.util.HashSet;
import java.util.Set;
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
        return fieldAnalyser.getFieldAnalysis();
    }

    default ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
        if (parameterInfo.parameterAnalysis.isSet()) return parameterInfo.parameterAnalysis.get();
        ParameterAnalyser parameterAnalyser = getParameterAnalyser(parameterInfo);
        if (parameterAnalyser != null) {
            return parameterAnalyser.getParameterAnalysis();
        }
        MethodAnalysis methodAnalysis = getMethodAnalysis(parameterInfo.owner);
        if (methodAnalysis != null) {
            return methodAnalysis.getParameterAnalyses().get(parameterInfo.index);
        }
        AnalyserContext parent = getParent();
        if (parent != null) return parent.getParameterAnalysis(parameterInfo);

        throw new UnsupportedOperationException("Parameter analysis of " + parameterInfo.fullyQualifiedName() + " not yet set");
    }

    default TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        TypeAnalyser typeAnalyser = getTypeAnalyser(typeInfo);
        if (typeAnalyser == null) {
            AnalyserContext parent = getParent();
            if (parent != null) return parent.getTypeAnalysis(typeInfo);
            return typeInfo.typeAnalysis.get(typeInfo.fullyQualifiedName);
        }
        return typeAnalyser.getTypeAnalysis();
    }

    default TypeAnalysis getTypeAnalysisNullWhenAbsent(TypeInfo typeInfo) {
        TypeAnalyser typeAnalyser = getTypeAnalyser(typeInfo);
        if (typeAnalyser == null) {
            AnalyserContext parent = getParent();
            if (parent != null) return parent.getTypeAnalysisNullWhenAbsent(typeInfo);
            return typeInfo.typeAnalysis.getOrDefaultNull();
        }
        return typeAnalyser.getTypeAnalysis();
    }

    default MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = getMethodAnalyser(methodInfo);
        if (methodAnalyser == null) {
            AnalyserContext parent = getParent();
            if (parent != null) return parent.getMethodAnalysis(methodInfo);
            return methodInfo.methodAnalysis.get(methodInfo.fullyQualifiedName);
        }
        return methodAnalyser.getMethodAnalysis();
    }

    default MethodAnalysis getMethodAnalysisNullWhenAbsent(MethodInfo methodInfo) {
        MethodAnalyser methodAnalyser = getMethodAnalyser(methodInfo);
        if (methodAnalyser == null) {
            AnalyserContext parent = getParent();
            if (parent != null) return parent.getMethodAnalysisNullWhenAbsent(methodInfo);
            return methodInfo.methodAnalysis.getOrDefaultNull();
        }
        return methodAnalyser.getMethodAnalysis();
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

    /**
     * Important that we can override this (even if we run in a non-ALL mode for the source,
     * we want to run the AnnotatedAPI analyser in ALL mode).
     *
     * @return the program that determines which analyser components will be executed
     */
    default AnalyserProgram getAnalyserProgram() {
        return getConfiguration().analyserConfiguration().analyserProgram();
    }

    default DV safeContainer(ParameterizedType parameterizedType) {
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (parameterizedType.arrays > 0) {
            return MultiLevel.CONTAINER_DV;
        }
        if (bestType == null) {
            // unbound type parameter, null constant
            return MultiLevel.NOT_CONTAINER_DV;
        }
        TypeAnalysis typeAnalysis = getTypeAnalysisNullWhenAbsent(bestType);
        if (typeAnalysis == null) {
            return null;
        }
        DV dv = typeAnalysis.getProperty(Property.CONTAINER);
        if (dv.isDelayed()) {
            return dv;
        }
        if (bestType.isFinal(this) || bestType.isInterface() && dv.equals(MultiLevel.CONTAINER_DV)) {
            return dv;
        }
        return null;
    }

    default DV immutableOfHiddenContentInTypeParameters(ParameterizedType parameterizedType, TypeInfo currentType) {
        SetOfTypes hiddenContentTypes = typeParametersOf(parameterizedType);
        return hiddenContentTypes.types().stream()
                .map(pt -> defaultImmutable(pt, true, currentType))
                .reduce(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, DV::min);
    }

    private SetOfTypes typeParametersOf(ParameterizedType parameterizedType) {
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (bestType == null) {
            // unbound type parameter, with or without arrays
            return new SetOfTypes(Set.of(parameterizedType.copyWithoutArrays()));
        }
        /*
        archetypal situation 1: pt == Set<String>; we want to return String

        Formally, the type has formal type parameters. They must be part of the hidden
        content; so we return the values of the concrete parameters
         */
        TypeInspection typeInspection = getTypeInspection(bestType);
        Set<ParameterizedType> result = new HashSet<>();
        for (TypeParameter tp : typeInspection.typeParameters()) {
            if (tp.isUnbound() && parameterizedType.parameters.size() > tp.getIndex()) {
                result.add(parameterizedType.parameters.get(tp.getIndex()));
            }
        }
        return new SetOfTypes(result);
    }

    private static DV typeAnalysisNotAvailable(TypeInfo bestType) {
        return bestType.delay(CauseOfDelay.Cause.TYPE_ANALYSIS);
    }

}
