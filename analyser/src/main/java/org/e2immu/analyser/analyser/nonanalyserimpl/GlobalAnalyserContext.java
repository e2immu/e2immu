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

package org.e2immu.analyser.analyser.nonanalyserimpl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.*;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.ImportantClasses;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.support.SetOnceMap;

public class GlobalAnalyserContext implements AnalyserContext {

    private final SetOnceMap<MethodInfo, MethodAnalysis> methodAnalyses;
    private final SetOnceMap<TypeInfo, TypeAnalysis> typeAnalyses;
    private final SetOnceMap<FieldInfo, FieldAnalysis> fieldAnalyses;
    private final SetOnceMap<ParameterInfo, ParameterAnalysis> parameterAnalyses;

    private final Primitives primitives;
    private final boolean inAnnotatedAPIAnalysis;
    private final Configuration configuration;
    private final ImportantClasses importantClasses;
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;


    public GlobalAnalyserContext(Primitives primitives,
                                 Configuration configuration,
                                 ImportantClasses importantClasses,
                                 E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                                 boolean inAnnotatedAPIAnalysis) {
        this(primitives, configuration, importantClasses, e2ImmuAnnotationExpressions, inAnnotatedAPIAnalysis,
                new SetOnceMap<>(), new SetOnceMap<>(), new SetOnceMap<>(), new SetOnceMap<>());
    }

    private GlobalAnalyserContext(Primitives primitives,
                                  Configuration configuration,
                                  ImportantClasses importantClasses,
                                  E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                                  boolean inAnnotatedAPIAnalysis,
                                  SetOnceMap<TypeInfo, TypeAnalysis> typeAnalyses,
                                  SetOnceMap<MethodInfo, MethodAnalysis> methodAnalyses,
                                  SetOnceMap<FieldInfo, FieldAnalysis> fieldAnalyses,
                                  SetOnceMap<ParameterInfo, ParameterAnalysis> parameterAnalyses) {
        this.primitives = primitives;
        this.configuration = configuration;
        this.importantClasses = importantClasses;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        this.inAnnotatedAPIAnalysis = inAnnotatedAPIAnalysis;
        this.typeAnalyses = typeAnalyses;
        this.methodAnalyses = methodAnalyses;
        this.fieldAnalyses = fieldAnalyses;
        this.parameterAnalyses = parameterAnalyses;
    }

    public GlobalAnalyserContext with(boolean inAnnotatedAPIAnalysis) {
        return new GlobalAnalyserContext(primitives, configuration, importantClasses,
                e2ImmuAnnotationExpressions, inAnnotatedAPIAnalysis, typeAnalyses, methodAnalyses, fieldAnalyses,
                parameterAnalyses);
    }

    @Override
    public ImportantClasses importantClasses() {
        return importantClasses;
    }

    @Override
    public Primitives getPrimitives() {
        return primitives;
    }

    @Override
    public Configuration getConfiguration() {
        return configuration;
    }

    @Override
    public boolean inAnnotatedAPIAnalysis() {
        return inAnnotatedAPIAnalysis;
    }

    @Override
    public E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions() {
        return e2ImmuAnnotationExpressions;
    }

    @Override
    public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        return methodAnalyses.get(methodInfo);
    }

    @Override
    public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
        return parameterAnalyses.get(parameterInfo);
    }

    @Override
    public TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        return typeAnalyses.get(typeInfo);
    }

    @Override
    public TypeAnalysis getTypeAnalysisNullWhenAbsent(TypeInfo typeInfo) {
        return typeAnalyses.getOrDefaultNull(typeInfo);
    }

    @Override
    public FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
        return fieldAnalyses.get(fieldInfo);
    }

    @Override
    public MethodInspection.Builder newMethodInspectionBuilder(Identifier identifier, TypeInfo typeInfo, String methodName) {
        throw new UnsupportedOperationException();
    }

    public void write(Analysis analysis) {
        if (analysis instanceof TypeAnalysis typeAnalysis) {
            typeAnalyses.put(typeAnalysis.getTypeInfo(), typeAnalysis);
        } else if (analysis instanceof MethodAnalysis methodAnalysis) {
            methodAnalyses.put(methodAnalysis.getMethodInfo(), methodAnalysis);
        } else if (analysis instanceof FieldAnalysis fieldAnalysis) {
            fieldAnalyses.put(fieldAnalysis.getFieldInfo(), fieldAnalysis);
        } else if (analysis instanceof ParameterAnalysis parameterAnalysis) {
            parameterAnalyses.put(parameterAnalysis.getParameterInfo(), parameterAnalysis);
        } else throw new UnsupportedOperationException();
    }
}
