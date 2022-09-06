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
import java.util.stream.Collectors;
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

    default DV immutableOfHiddenContentInTypeParameters(ParameterizedType parameterizedType) {
        SetOfTypes hiddenContentTypes = typeParametersOf(parameterizedType);
        return hiddenContentTypes.types().stream()
                .map(this::typeImmutable)
                .reduce(MultiLevel.EFFECTIVELY_IMMUTABLE_DV, DV::min);
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

    /*
    Front method for typeAnalysis.getHiddenContentTypes(), when there may not be a type.

    T, T[], T[][]  --> T
    Function<T, R> --> hidden content(R)
     */
    default SetOfTypes hiddenContentTypes(ParameterizedType type) {
        if (type.isFunctionalInterface(this)) {
            ParameterizedType returnType = type.findSingleAbstractMethodOfInterface(this)
                    .getConcreteReturnType(getPrimitives());
            return hiddenContentTypes(returnType);
        }
        if (type.typeParameter != null) {
            return new SetOfTypes(Set.of(type.copyWithoutArrays()));
        }
        TypeAnalysis typeAnalysis = getTypeAnalysis(type.bestTypeInfo());
        return typeAnalysis.getHiddenContentTypes();
    }


    /*
    Intersection of hidden content types.

    The default implementation would be to compute a set-wise intersection; e.g., if we're computing the hidden content
    type intersection of Set<Integer> and TreeSet<Integer>, we'd have 2x Integer, and return Integer.
    Examples where !this.equals(other) are???: IMPROVE, make some example.

    However, if this contains "String, Integer" and other contains "Map.Entry<String,Integer>"
    (or vice versa, this operation must be symmetric), then we would expect the Map.Entry<String,Integer> to be included,
    among the String and Integer. See: MethodReference_3, stream() method.
     */
    default SetOfTypes intersectionOfHiddenContent(ParameterizedType pt1, TypeAnalysis t1, ParameterizedType pt2, TypeAnalysis t2) {
        SetOfTypes hidden1 = t1.getHiddenContentTypes().translate(this, pt1);
        SetOfTypes hidden2 = t2.getHiddenContentTypes().translate(this, pt2);
        SetOfTypes plainIntersection = hidden1.intersection(hidden2);
        if (plainIntersection.isEmpty() && !hidden1.isEmpty() && !hidden2.isEmpty()) {
            // for now, we're hard-coding the Map.Entry situation
            Set<ParameterizedType> hidden1Hidden = hidden1.types().stream()
                    .filter(t -> t.bestTypeInfo() != null)
                    .flatMap(t -> getTypeAnalysis(t.bestTypeInfo()).getHiddenContentTypes()
                            .translate(this, t).types().stream())
                    .collect(Collectors.toUnmodifiableSet());
            Set<ParameterizedType> hidden2Hidden = hidden2.types().stream()
                    .filter(t -> t.bestTypeInfo() != null)
                    .flatMap(t -> getTypeAnalysis(t.bestTypeInfo()).getHiddenContentTypes()
                            .translate(this, t).types().stream())
                    .collect(Collectors.toUnmodifiableSet());
            if (hidden1Hidden.isEmpty() && hidden2Hidden.containsAll(hidden1.types()) ||
                    hidden2Hidden.isEmpty() && hidden1Hidden.containsAll(hidden2.types())) {
                return hidden1.union(hidden2);
            }
        }
        return plainIntersection;
    }
}
