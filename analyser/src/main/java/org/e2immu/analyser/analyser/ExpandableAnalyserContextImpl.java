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
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.pattern.PatternMatcher;
import org.e2immu.analyser.util.SetOnceMap;

import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

public class ExpandableAnalyserContextImpl implements AnalyserContext {

    public final AnalyserContext parent;
    private final SetOnceMap<MethodInfo, MethodAnalyser> methodAnalysers = new SetOnceMap<>();
    private final SetOnceMap<TypeInfo, TypeAnalyser> typeAnalysers = new SetOnceMap<>();
    private final SetOnceMap<FieldInfo, FieldAnalyser> fieldAnalysers = new SetOnceMap<>();
    private final SetOnceMap<ParameterInfo, ParameterAnalyser> parameterAnalysers = new SetOnceMap<>();

    public ExpandableAnalyserContextImpl(AnalyserContext parent) {
        this.parent = Objects.requireNonNull(parent);
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
    public PatternMatcher<StatementAnalyser> getPatternMatcher() {
        return parent.getPatternMatcher();
    }

    @Override
    public TypeInfo getPrimaryType() {
        return parent.getPrimaryType();
    }

    @Override
    public Stream<MethodAnalyser> methodAnalyserStream() {
        return Stream.concat(parent.methodAnalyserStream(), this.methodAnalysers.stream().map(Map.Entry::getValue));
    }

    @Override
    public MethodAnalyser getMethodAnalyser(MethodInfo methodInfo) {
        MethodAnalyser ma = this.methodAnalysers.getOrDefault(methodInfo, null);
        if (ma == null) {
            return parent.getMethodAnalyser(methodInfo);
        }
        return ma;
    }

    @Override
    public MethodAnalysis getMethodAnalysis(MethodInfo methodInfo) {
        MethodAnalyser ma = this.methodAnalysers.getOrDefault(methodInfo, null);
        if (ma != null) return ma.methodAnalysis;
        return parent.getMethodAnalysis(methodInfo);
    }

    @Override
    public ParameterAnalyser getParameterAnalyser(ParameterInfo parameterInfo) {
        ParameterAnalyser ma = this.parameterAnalysers.getOrDefault(parameterInfo, null);
        if (ma == null) {
            return parent.getParameterAnalyser(parameterInfo);
        }
        return ma;
    }

    @Override
    public ParameterAnalysis getParameterAnalysis(ParameterInfo parameterInfo) {
        ParameterAnalyser pa = this.parameterAnalysers.getOrDefault(parameterInfo, null);
        if (pa != null) return pa.parameterAnalysis;
        return getMethodAnalysis(parameterInfo.owner).getParameterAnalyses().get(parameterInfo.index);
    }

    @Override
    public TypeAnalyser getTypeAnalyser(TypeInfo typeInfo) {
        TypeAnalyser ta = this.typeAnalysers.getOrDefault(typeInfo, null);
        if (ta == null) {
            return parent.getTypeAnalyser(typeInfo);
        }
        return ta;
    }

    @Override
    public TypeAnalysis getTypeAnalysis(TypeInfo typeInfo) {
        TypeAnalyser ta = this.typeAnalysers.getOrDefault(typeInfo, null);
        if (ta != null) return ta.typeAnalysis;
        return parent.getTypeAnalysis(typeInfo);
    }

    @Override
    public Stream<FieldAnalyser> fieldAnalyserStream() {
        return Stream.concat(parent.fieldAnalyserStream(), this.fieldAnalysers.stream().map(Map.Entry::getValue));
    }

    @Override
    public FieldAnalysis getFieldAnalysis(FieldInfo fieldInfo) {
        FieldAnalyser fa = this.fieldAnalysers.getOrDefault(fieldInfo, null);
        if (fa != null) return fa.fieldAnalysis;
        return parent.getFieldAnalysis(fieldInfo);
    }

    public void addPrimaryTypeAnalyser(PrimaryTypeAnalyser pta) {
        pta.analysers.forEach(analyser -> {
            if (analyser instanceof MethodAnalyser ma) this.methodAnalysers.put(ma.methodInfo, ma);
            else if (analyser instanceof TypeAnalyser ta) this.typeAnalysers.put(ta.typeInfo, ta);
            else if (analyser instanceof FieldAnalyser fa) this.fieldAnalysers.put(fa.fieldInfo, fa);
            else if (analyser instanceof ParameterAnalyser pa) this.parameterAnalysers.put(pa.parameterInfo, pa);
        });
    }

    public void addAll(ExpandableAnalyserContextImpl previous) {
        methodAnalysers.putAll(previous.methodAnalysers);
        typeAnalysers.putAll(previous.typeAnalysers);
        fieldAnalysers.putAll(previous.fieldAnalysers);
        parameterAnalysers.putAll(previous.parameterAnalysers);
    }
}
