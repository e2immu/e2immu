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
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.ImportantClasses;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.support.SetOnceMap;

import java.util.Objects;
import java.util.stream.Stream;

public class LocalAnalyserContext implements AnalyserContext {

    private final AnalyserContext parent;
    private final SetOnceMap<MethodInfo, MethodAnalyser> methodAnalysers;
    private final SetOnceMap<TypeInfo, TypeAnalyser> typeAnalysers;
    private final SetOnceMap<FieldInfo, FieldAnalyser> fieldAnalysers;
    private final SetOnceMap<ParameterInfo, ParameterAnalyser> parameterAnalysers;

    private final Primitives primitives;
    private final boolean inAnnotatedAPIAnalysis;
    private final Configuration configuration;
    private final ImportantClasses importantClasses;
    private final E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions;

    public LocalAnalyserContext(AnalyserContext parent) {
        this(Objects.requireNonNull(parent),
                parent.getPrimitives(), parent.getConfiguration(), parent.importantClasses(),
                parent.getE2ImmuAnnotationExpressions(), parent.inAnnotatedAPIAnalysis(),
                new SetOnceMap<>(), new SetOnceMap<>(), new SetOnceMap<>(), new SetOnceMap<>());
    }

    public LocalAnalyserContext(Primitives primitives,
                                Configuration configuration,
                                ImportantClasses importantClasses,
                                E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                                boolean inAnnotatedAPIAnalysis) {
        this(null, primitives, configuration, importantClasses, e2ImmuAnnotationExpressions, inAnnotatedAPIAnalysis,
                new SetOnceMap<>(), new SetOnceMap<>(), new SetOnceMap<>(), new SetOnceMap<>());
    }

    private LocalAnalyserContext(AnalyserContext parent,
                                 Primitives primitives,
                                 Configuration configuration,
                                 ImportantClasses importantClasses,
                                 E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions,
                                 boolean inAnnotatedAPIAnalysis,
                                 SetOnceMap<TypeInfo, TypeAnalyser> typeAnalysers,
                                 SetOnceMap<MethodInfo, MethodAnalyser> methodAnalysers,
                                 SetOnceMap<FieldInfo, FieldAnalyser> fieldAnalysers,
                                 SetOnceMap<ParameterInfo, ParameterAnalyser> parameterAnalysers) {
        this.parent = parent;
        this.primitives = primitives;
        this.configuration = configuration;
        this.importantClasses = importantClasses;
        this.e2ImmuAnnotationExpressions = e2ImmuAnnotationExpressions;
        this.inAnnotatedAPIAnalysis = inAnnotatedAPIAnalysis;
        this.typeAnalysers = typeAnalysers;
        this.methodAnalysers = methodAnalysers;
        this.fieldAnalysers = fieldAnalysers;
        this.parameterAnalysers = parameterAnalysers;
    }

    public LocalAnalyserContext with(boolean inAnnotatedAPIAnalysis) {
        return new LocalAnalyserContext(parent, primitives, configuration, importantClasses,
                e2ImmuAnnotationExpressions, inAnnotatedAPIAnalysis, typeAnalysers, methodAnalysers, fieldAnalysers,
                parameterAnalysers);
    }

    @Override
    public ImportantClasses importantClasses() {
        return importantClasses;
    }

    @Override
    public AnalyserContext getParent() {
        return parent;
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
    public Stream<MethodAnalyser> methodAnalyserStream() {
        if (parent == null) return methodAnalysers.valueStream();
        return Stream.concat(parent.methodAnalyserStream(), this.methodAnalysers.valueStream());
    }

    @Override
    public MethodAnalyser getMethodAnalyser(MethodInfo methodInfo) {
        MethodAnalyser ma = this.methodAnalysers.getOrDefaultNull(methodInfo);
        if (ma == null && parent != null) {
            return parent.getMethodAnalyser(methodInfo);
        }
        return ma;
    }

    @Override
    public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalyser ma = this.methodAnalysers.getOrDefaultNull(methodInfo);
        if (ma != null) return ma.getMethodAnalysis();
        if (parent != null) return parent.getMethodAnalysis(methodInfo);
        throw new UnsupportedOperationException("Cannot find method analysis of " + methodInfo.fullyQualifiedName);
    }

    @Override
    public ParameterAnalyser getParameterAnalyser(ParameterInfo parameterInfo) {
        ParameterAnalyser ma = this.parameterAnalysers.getOrDefaultNull(parameterInfo);
        if (ma == null && parent != null) {
            return parent.getParameterAnalyser(parameterInfo);
        }
        return ma;
    }

    @Override
    public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
        ParameterAnalyser pa = this.parameterAnalysers.getOrDefaultNull(parameterInfo);
        if (pa != null) return pa.getParameterAnalysis();
        MethodAnalysis methodAnalysis = getMethodAnalysis(parameterInfo.owner);
        assert methodAnalysis != null : "Cannot find method analysis of " + parameterInfo.owner.fullyQualifiedName;
        return methodAnalysis.getParameterAnalyses().get(parameterInfo.index);
    }

    @Override
    public TypeAnalyser getTypeAnalyser(TypeInfo typeInfo) {
        TypeAnalyser ta = this.typeAnalysers.getOrDefaultNull(typeInfo);
        if (ta == null && parent != null) {
            return parent.getTypeAnalyser(typeInfo);
        }
        return ta;
    }

    @Override
    public FieldAnalyser getFieldAnalyser(FieldInfo fieldInfo) {
        FieldAnalyser fa = this.fieldAnalysers.getOrDefaultNull(fieldInfo);
        if (fa == null && parent != null) {
            return parent.getFieldAnalyser(fieldInfo);
        }
        return fa;
    }

    @Override
    public TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        TypeAnalyser typeAnalyser = this.typeAnalysers.getOrDefaultNull(typeInfo);
        if (typeAnalyser != null) return typeAnalyser.getTypeAnalysis();
        if (parent != null) return parent.getTypeAnalysis(typeInfo);
        throw new UnsupportedOperationException("Cannot find type analysis of " + typeInfo.fullyQualifiedName);
    }

    @Override
    public TypeAnalysis getTypeAnalysisNullWhenAbsent(TypeInfo typeInfo) {
        TypeAnalyser typeAnalyser = this.typeAnalysers.getOrDefaultNull(typeInfo);
        if (typeAnalyser != null) return typeAnalyser.getTypeAnalysis();
        return parent.getTypeAnalysisNullWhenAbsent(typeInfo);
    }

    @Override
    public Stream<FieldAnalyser> fieldAnalyserStream() {
        return Stream.concat(parent.fieldAnalyserStream(), this.fieldAnalysers.valueStream());
    }

    @Override
    public FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
        FieldAnalyser fa = this.fieldAnalysers.getOrDefaultNull(fieldInfo);
        if (fa != null) return fa.getFieldAnalysis();
        if (parent != null) return parent.getFieldAnalysis(fieldInfo);
        throw new UnsupportedOperationException("Cannot find field analysis of " + fieldInfo.fullyQualifiedName);
    }

    @Override
    public MethodInspection.Builder newMethodInspectionBuilder(Identifier identifier, TypeInfo typeInfo, String methodName) {
        assert parent != null : "Not for an ExpandableAnalyser to implement";
        return parent.newMethodInspectionBuilder(identifier, typeInfo, methodName);
    }

    public void addPrimaryTypeAnalyser(PrimaryTypeAnalyser pta) {
        pta.loopOverAnalysers(analyser -> {
            if (analyser instanceof MethodAnalyser ma && !this.methodAnalysers.isSet(ma.getMethodInfo())) {
                this.methodAnalysers.put(ma.getMethodInfo(), ma);
            } else if (analyser instanceof TypeAnalyser ta && !this.typeAnalysers.isSet(ta.getTypeInfo())) {
                this.typeAnalysers.put(ta.getTypeInfo(), ta);
            } else if (analyser instanceof FieldAnalyser fa && !this.fieldAnalysers.isSet(fa.getFieldInfo())) {
                this.fieldAnalysers.put(fa.getFieldInfo(), fa);
            } else if (analyser instanceof ParameterAnalyser pa && !this.parameterAnalysers.isSet(pa.getParameterInfo())) {
                this.parameterAnalysers.put(pa.getParameterInfo(), pa);
            }
        });
    }

    public void addAll(LocalAnalyserContext previous) {
        methodAnalysers.putAll(previous.methodAnalysers);
        typeAnalysers.putAll(previous.typeAnalysers);
        fieldAnalysers.putAll(previous.fieldAnalysers);
        parameterAnalysers.putAll(previous.parameterAnalysers);
    }
}
