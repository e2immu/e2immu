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
import org.e2immu.analyser.analyser.util.Cache;
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

    public final AnalyserContext parent;
    private final SetOnceMap<MethodInfo, MethodAnalyser> methodAnalysers = new SetOnceMap<>();
    private final SetOnceMap<TypeInfo, TypeAnalyser> typeAnalysers = new SetOnceMap<>();
    private final SetOnceMap<FieldInfo, FieldAnalyser> fieldAnalysers = new SetOnceMap<>();

    public LocalAnalyserContext(AnalyserContext parent) {
        this.parent = Objects.requireNonNull(parent);
    }

    @Override
    public ImportantClasses importantClasses() {
        return parent.importantClasses();
    }

    @Override
    public AnalyserContext getParent() {
        return parent;
    }

    @Override
    public Primitives getPrimitives() {
        return parent.getPrimitives();
    }

    @Override
    public Configuration getConfiguration() {
        return parent.getConfiguration();
    }

    @Override
    public boolean inAnnotatedAPIAnalysis() {
        return parent.inAnnotatedAPIAnalysis();
    }

    @Override
    public E2ImmuAnnotationExpressions getE2ImmuAnnotationExpressions() {
        return parent.getE2ImmuAnnotationExpressions();
    }

    @Override
    public Stream<MethodAnalyser> methodAnalyserStream(TypeInfo primaryType) {
        assert methodAnalysers.stream().allMatch(e -> e.getKey().typeInfo.primaryType() == primaryType);
        return Stream.concat(parent.methodAnalyserStream(primaryType), this.methodAnalysers.valueStream());
    }

    @Override
    public MethodAnalyser getMethodAnalyser(MethodInfo methodInfo) {
        MethodAnalyser ma = this.methodAnalysers.getOrDefaultNull(methodInfo);
        if (ma == null) {
            return parent.getMethodAnalyser(methodInfo);
        }
        return ma;
    }

    @Override
    public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalyser ma = this.methodAnalysers.getOrDefaultNull(methodInfo);
        if (ma != null) return ma.getMethodAnalysis();
        return parent.getMethodAnalysis(methodInfo);
    }

    @Override
    public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
        return Objects.requireNonNull(getParameterAnalysisNullWhenAbsent(parameterInfo),
                "Cannot find parameter analysis for " + parameterInfo);
    }

    @Override
    public ParameterAnalysis getParameterAnalysisNullWhenAbsent(ParameterInfo parameterInfo) {
        MethodAnalyser methodAnalyser = methodAnalysers.getOrDefaultNull(parameterInfo.owner);
        if (methodAnalyser != null) return methodAnalyser.getParameterAnalyses().get(parameterInfo.index);
        return parent.getParameterAnalysisNullWhenAbsent(parameterInfo);
    }

    @Override
    public TypeAnalyser getTypeAnalyser(TypeInfo typeInfo) {
        TypeAnalyser ta = this.typeAnalysers.getOrDefaultNull(typeInfo);
        if (ta == null) {
            return parent.getTypeAnalyser(typeInfo);
        }
        return ta;
    }

    @Override
    public TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        TypeAnalyser typeAnalyser = this.typeAnalysers.getOrDefaultNull(typeInfo);
        if (typeAnalyser != null) return typeAnalyser.getTypeAnalysis();
        return parent.getTypeAnalysis(typeInfo);
    }

    @Override
    public TypeAnalysis getTypeAnalysisNullWhenAbsent(TypeInfo typeInfo) {
        TypeAnalyser typeAnalyser = this.typeAnalysers.getOrDefaultNull(typeInfo);
        if (typeAnalyser != null) return typeAnalyser.getTypeAnalysis();
        return parent.getTypeAnalysisNullWhenAbsent(typeInfo);
    }

    @Override
    public Stream<FieldAnalyser> fieldAnalyserStream(TypeInfo typeInfo) {
        return Stream.concat(parent.fieldAnalyserStream(typeInfo),
                this.fieldAnalysers.valueStream().filter(fa -> fa.getFieldInfo().owner == typeInfo));
    }

    @Override
    public FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
        FieldAnalyser fa = this.fieldAnalysers.getOrDefaultNull(fieldInfo);
        if (fa != null) return fa.getFieldAnalysis();
        return parent.getFieldAnalysis(fieldInfo);
    }

    @Override
    public MethodInspection.Builder newMethodInspectionBuilder(Identifier identifier, TypeInfo typeInfo, String methodName) {
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
            }
        });
    }

    public void addAll(LocalAnalyserContext previous) {
        methodAnalysers.putAll(previous.methodAnalysers);
        typeAnalysers.putAll(previous.typeAnalysers);
        fieldAnalysers.putAll(previous.fieldAnalysers);
    }

    @Override
    public TypeInspection getTypeInspection(TypeInfo typeInfo) {
        if (typeInfo.typeInspection.isSet()) return typeInfo.typeInspection.get();
        return parent.getTypeInspection(typeInfo);
    }

    @Override
    public Cache getCache() {
        return parent.getCache();
    }
}
