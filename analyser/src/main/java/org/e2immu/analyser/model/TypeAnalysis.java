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
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Messages;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.AnnotationMode;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.RESOLVE;
import static org.e2immu.analyser.util.Logger.log;

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

    // computed during inspection

    public final SetOnce<Set<TypeInfo>> circularDependencies = new SetOnce<>();

    private final Map<ObjectFlow, ObjectFlow> constantObjectFlows = new HashMap<>();

    public ObjectFlow ensureConstantObjectFlow(ObjectFlow objectFlow) {
        if (objectFlow == ObjectFlow.NO_FLOW) throw new UnsupportedOperationException();
        if (constantObjectFlows.containsKey(objectFlow)) return constantObjectFlows.get(objectFlow);
        this.constantObjectFlows.put(objectFlow, objectFlow);
        return objectFlow;
    }

    public Stream<ObjectFlow> getConstantObjectFlows() {
        return constantObjectFlows.values().stream();
    }

    // from label to condition BEFORE (used by @Mark and @Only(before="label"))
    public final SetOnceMap<String, Value> approvedPreconditions = new SetOnceMap<>();

    public boolean isEventual() {
        return !approvedPreconditions.isEmpty();
    }

    public Set<String> marksRequiredForImmutable() {
        return approvedPreconditions.stream().map(Map.Entry::getKey).collect(Collectors.toUnmodifiableSet());
    }

    public String allLabelsRequiredForImmutable() {
        return String.join(",", marksRequiredForImmutable());
    }

    public final SetOnce<Set<ParameterizedType>> implicitlyImmutableDataTypes = new SetOnce<>();

    @Override
    public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {

        // @ExtensionClass
        if (getProperty(VariableProperty.EXTENSION_CLASS) == Level.TRUE) {
            annotations.put(e2ImmuAnnotationExpressions.extensionClass.get(), true);
        }

        // @UtilityClass
        if (getProperty(VariableProperty.UTILITY_CLASS) == Level.TRUE) {
            annotations.put(e2ImmuAnnotationExpressions.utilityClass.get(), true);
        }

        // @Singleton
        if (getProperty(VariableProperty.SINGLETON) == Level.TRUE) {
            annotations.put(e2ImmuAnnotationExpressions.singleton.get(), true);
        }

        int immutable = getProperty(VariableProperty.IMMUTABLE);
        doImmutableContainer(e2ImmuAnnotationExpressions, immutable, false);

        // @Independent
        int independent = getProperty(VariableProperty.INDEPENDENT);
        if (!MultiLevel.isAtLeastEventuallyE2Immutable(immutable)) {
            doIndependent(e2ImmuAnnotationExpressions, independent, typeInfo.isInterface());
        }
    }
}
