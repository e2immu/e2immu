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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.TypeContext;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.AnnotationMode;

import java.lang.annotation.ElementType;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class TypeAnalysis extends Analysis {

    public final TypeInfo typeInfo;

    public TypeAnalysis(TypeInfo typeInfo) {
        super(typeInfo.hasBeenDefined(), typeInfo.simpleName);
        this.typeInfo = typeInfo;
    }

    @Override
    protected Location location() {
        return new Location(typeInfo);
    }

    @Override
    public AnnotationMode annotationMode() {
        return typeInfo.typeInspection.get().annotationMode;
    }

    // to avoid repetitions
    public final SetOnce<Boolean> startedPostAnalysisIntoNestedTypes = new SetOnce<>();

    public final SetOnce<Boolean> doNotAllowDelaysOnNotModified = new SetOnce<>();

    protected Set<ElementType> extractWhere(AnnotationExpression annotationExpression) {
        ElementType[] elements = annotationExpression.extract("where", NOT_NULL_WHERE_ALL);
        return Arrays.stream(elements).collect(Collectors.toSet());
    }

    @Override
    public Map<VariableProperty, AnnotationExpression> oppositesMap(TypeContext typeContext) {
        return Map.of();
    }

    private final Set<ObjectFlow> constantObjectFlows = new HashSet<>();

    public void addConstantObjectFlow(ObjectFlow objectFlow) {
        if (objectFlow == ObjectFlow.NO_FLOW) throw new UnsupportedOperationException();
        this.constantObjectFlows.add(objectFlow);
    }

    public Stream<ObjectFlow> getConstantObjectFlows() {
        return constantObjectFlows.stream();
    }

    public final SetOnceMap<Value, String> approvedPreconditions = new SetOnceMap<>();

    public boolean isEventual() {
        return !approvedPreconditions.isEmpty();
    }

    public Set<String> marksRequiredForImmutable() {
        return approvedPreconditions.stream().map(Map.Entry::getValue).collect(Collectors.toSet());
    }
}
