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

import com.google.common.collect.ImmutableList;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.objectflow.ObjectFlow;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.analyser.util.AddOnceSet;
import org.e2immu.analyser.util.SetOnce;
import org.e2immu.analyser.util.SetOnceMap;
import org.e2immu.annotation.AnnotationMode;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class TypeAnalysisImpl extends AnalysisImpl implements TypeAnalysis {

    private final TypeInfo typeInfo;
    private final Set<ObjectFlow> objectFlows;
    private final Map<String, Value> approvedPreconditions;
    private final Set<ParameterizedType> implicitlyImmutableDataTypes;
    private final Map<String, MethodInfo> aspects;
    private final List<Value> invariants;

    private TypeAnalysisImpl(TypeInfo typeInfo,
                             Map<VariableProperty, Integer> properties,
                             Map<AnnotationExpression, Boolean> annotations,
                             Set<ObjectFlow> objectFlows,
                             Map<String, Value> approvedPreconditions,
                             Set<ParameterizedType> implicitlyImmutableDataTypes,
                             Map<String, MethodInfo> aspects,
                             List<Value> invariants) {
        super(properties, annotations);
        this.typeInfo = typeInfo;
        this.approvedPreconditions = approvedPreconditions;
        this.objectFlows = objectFlows;
        this.implicitlyImmutableDataTypes = implicitlyImmutableDataTypes;
        this.aspects = Objects.requireNonNull(aspects);
        this.invariants = invariants;
    }

    @Override
    public List<Value> getInvariants() {
        return invariants;
    }

    @Override
    public Map<String, MethodInfo> getAspects() {
        return aspects;
    }

    @Override
    public int getProperty(VariableProperty variableProperty) {
        return getTypeProperty(variableProperty);
    }

    @Override
    public Location location() {
        return new Location(typeInfo);
    }

    @Override
    public AnnotationMode annotationMode() {
        return typeInfo.typeInspection.get().annotationMode();
    }

    @Override
    public Set<ObjectFlow> getConstantObjectFlows() {
        return objectFlows;
    }

    @Override
    public Map<String, Value> getApprovedPreconditions() {
        return approvedPreconditions;
    }

    @Override
    public Set<ParameterizedType> getImplicitlyImmutableDataTypes() {
        return implicitlyImmutableDataTypes;
    }

    public static class Builder extends AbstractAnalysisBuilder implements TypeAnalysis {
        public final TypeInfo typeInfo;
        public final AddOnceSet<ObjectFlow> constantObjectFlows = new AddOnceSet<>();

        // from label to condition BEFORE (used by @Mark and @Only(before="label"))
        public final SetOnceMap<String, Value> approvedPreconditions = new SetOnceMap<>();
        public final SetOnce<Set<ParameterizedType>> implicitlyImmutableDataTypes = new SetOnce<>();

        public final SetOnceMap<String, MethodInfo> aspects = new SetOnceMap<>();
        public final SetOnce<List<Value>> invariants = new SetOnce<>();

        public Builder(Primitives primitives, TypeInfo typeInfo) {
            super(primitives, typeInfo.simpleName);
            this.typeInfo = typeInfo;
        }

        @Override
        public boolean aspectsIsSet(String aspect) {
            return aspects.isSet(aspect);
        }

        @Override
        public Map<String, MethodInfo> getAspects() {
            return aspects.toImmutableMap();
        }

        @Override
        public List<Value> getInvariants() {
            return invariants.getOrElse(null);
        }

        @Override
        public int getProperty(VariableProperty variableProperty) {
            return getTypeProperty(variableProperty);
        }

        @Override
        public Location location() {
            return new Location(typeInfo);
        }

        @Override
        public AnnotationMode annotationMode() {
            return typeInfo.typeInspection.get().annotationMode();
        }

        @Override
        public Set<ObjectFlow> getConstantObjectFlows() {
            return constantObjectFlows.toImmutableSet();
        }

        @Override
        public Map<String, Value> getApprovedPreconditions() {
            return approvedPreconditions.toImmutableMap();
        }

        @Override
        public Set<ParameterizedType> getImplicitlyImmutableDataTypes() {
            return implicitlyImmutableDataTypes.getOrElse(Set.of());
        }

        @Override
        public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {

            // @ExtensionClass
            if (getProperty(VariableProperty.EXTENSION_CLASS) == Level.TRUE) {
                annotations.put(e2ImmuAnnotationExpressions.extensionClass, true);
            }

            // @UtilityClass
            if (getProperty(VariableProperty.UTILITY_CLASS) == Level.TRUE) {
                annotations.put(e2ImmuAnnotationExpressions.utilityClass, true);
            }

            // @Singleton
            if (getProperty(VariableProperty.SINGLETON) == Level.TRUE) {
                annotations.put(e2ImmuAnnotationExpressions.singleton, true);
            }

            int immutable = getProperty(VariableProperty.IMMUTABLE);
            doImmutableContainer(e2ImmuAnnotationExpressions, immutable, false);

            // @Independent
            int independent = getProperty(VariableProperty.INDEPENDENT);
            if (!MultiLevel.isAtLeastEventuallyE2Immutable(immutable)) {
                doIndependent(e2ImmuAnnotationExpressions, independent, typeInfo.isInterface());
            }
        }

        public TypeAnalysis build() {
            return new TypeAnalysisImpl(typeInfo,
                    properties.toImmutableMap(),
                    annotations.toImmutableMap(),
                    constantObjectFlows.toImmutableSet(),
                    approvedPreconditions.toImmutableMap(),
                    implicitlyImmutableDataTypes.isSet() ? implicitlyImmutableDataTypes.get() : Set.of(),
                    getAspects(),
                    ImmutableList.copyOf(invariants.getOrElse(List.of())));
        }
    }
}