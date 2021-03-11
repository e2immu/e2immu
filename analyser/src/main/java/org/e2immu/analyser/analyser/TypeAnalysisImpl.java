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
import org.e2immu.analyser.util.FlipSwitch;
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
    private final Map<FieldInfo, Expression> approvedPreconditionsE1;
    private final Map<FieldInfo, Expression> approvedPreconditionsE2;

    private final Set<ParameterizedType> implicitlyImmutableDataTypes;
    private final Map<String, MethodInfo> aspects;
    private final List<Expression> invariants;
    private final Set<FieldInfo> eventuallyImmutableFields;

    private TypeAnalysisImpl(TypeInfo typeInfo,
                             Map<VariableProperty, Integer> properties,
                             Map<AnnotationExpression, AnnotationCheck> annotations,
                             Set<ObjectFlow> objectFlows,
                             Map<FieldInfo, Expression> approvedPreconditionsE1,
                             Map<FieldInfo, Expression> approvedPreconditionsE2,
                             Set<FieldInfo> eventuallyImmutableFields,
                             Set<ParameterizedType> implicitlyImmutableDataTypes,
                             Map<String, MethodInfo> aspects,
                             List<Expression> invariants) {
        super(properties, annotations);
        this.typeInfo = typeInfo;
        this.approvedPreconditionsE1 = approvedPreconditionsE1;
        this.approvedPreconditionsE2 = approvedPreconditionsE2;
        this.objectFlows = objectFlows;
        this.implicitlyImmutableDataTypes = implicitlyImmutableDataTypes;
        this.aspects = Objects.requireNonNull(aspects);
        this.invariants = invariants;
        this.eventuallyImmutableFields = eventuallyImmutableFields;
    }

    @Override
    public Set<FieldInfo> getEventuallyImmutableFields() {
        return eventuallyImmutableFields;
    }

    @Override
    public List<Expression> getInvariants() {
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
    public Map<FieldInfo, Expression> getApprovedPreconditionsE1() {
        return approvedPreconditionsE1;
    }

    @Override
    public Map<FieldInfo, Expression> getApprovedPreconditionsE2() {
        return approvedPreconditionsE2;
    }

    @Override
    public Expression getApprovedPreconditions(boolean e2, FieldInfo fieldInfo) {
        return e2 ? approvedPreconditionsE2.get(fieldInfo) : approvedPreconditionsE1.get(fieldInfo);
    }

    @Override
    public boolean approvedPreconditionsIsSet(boolean e2, FieldInfo fieldInfo) {
        return e2 ? approvedPreconditionsE2.containsKey(fieldInfo) : approvedPreconditionsE1.containsKey(fieldInfo);
    }

    @Override
    public boolean approvedPreconditionsIsFrozen(boolean e2) {
        return true;
    }

    @Override
    public Set<ParameterizedType> getImplicitlyImmutableDataTypes() {
        return implicitlyImmutableDataTypes;
    }

    public static class CycleInfo {
        public final AddOnceSet<MethodInfo> nonModified = new AddOnceSet<>();
        public final FlipSwitch modified = new FlipSwitch();
    }

    public static class Builder extends AbstractAnalysisBuilder implements TypeAnalysis {
        public final TypeInfo typeInfo;
        public final AddOnceSet<ObjectFlow> constantObjectFlows = new AddOnceSet<>();

        // from label to condition BEFORE (used by @Mark and @Only(before="label"))
        private final SetOnceMap<FieldInfo, Expression> approvedPreconditionsE1 = new SetOnceMap<>();
        private final SetOnceMap<FieldInfo, Expression> approvedPreconditionsE2 = new SetOnceMap<>();
        public final AddOnceSet<FieldInfo> eventuallyImmutableFields = new AddOnceSet<>();

        public final SetOnce<Set<ParameterizedType>> implicitlyImmutableDataTypes = new SetOnce<>();

        public final SetOnceMap<String, MethodInfo> aspects = new SetOnceMap<>();
        public final SetOnce<List<Expression>> invariants = new SetOnce<>();

        public final SetOnceMap<Set<MethodInfo>, CycleInfo> nonModifiedCountForMethodCallCycle = new SetOnceMap<>();
        public final SetOnce<Boolean> ignorePrivateConstructorsForFieldValues = new SetOnce<>();

        public Builder(Primitives primitives, TypeInfo typeInfo) {
            super(primitives, typeInfo.simpleName);
            this.typeInfo = typeInfo;
        }

        @Override
        public Expression getApprovedPreconditions(boolean e2, FieldInfo fieldInfo) {
            return e2 ? approvedPreconditionsE2.get(fieldInfo) : approvedPreconditionsE1.get(fieldInfo);
        }

        @Override
        public boolean approvedPreconditionsIsSet(boolean e2, FieldInfo fieldInfo) {
            return e2 ? approvedPreconditionsE2.isSet(fieldInfo) : approvedPreconditionsE1.isSet(fieldInfo);
        }

        public void freezeApprovedPreconditionsE1() {
            approvedPreconditionsE1.freeze();
        }

        public boolean approvedPreconditionsIsEmpty(boolean e2) {
            return e2 ? approvedPreconditionsE2.isEmpty() : approvedPreconditionsE1.isEmpty();
        }

        public void putInApprovedPreconditionsE1(FieldInfo fieldInfo, Expression expression) {
            approvedPreconditionsE1.put(fieldInfo, expression);
        }

        public boolean approvedPreconditionsIsFrozen(boolean e2) {
            return e2 ? approvedPreconditionsE2.isFrozen() : approvedPreconditionsE1.isFrozen();
        }

        public void freezeApprovedPreconditionsE2() {
            approvedPreconditionsE2.freeze();
        }

        public void putInApprovedPreconditionsE2(FieldInfo fieldInfo, Expression expression) {
            approvedPreconditionsE2.put(fieldInfo, expression);
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
        public List<Expression> getInvariants() {
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
        public Map<FieldInfo, Expression> getApprovedPreconditionsE1() {
            return approvedPreconditionsE1.toImmutableMap();
        }

        @Override
        public Map<FieldInfo, Expression> getApprovedPreconditionsE2() {
            return approvedPreconditionsE2.toImmutableMap();
        }

        @Override
        public Set<ParameterizedType> getImplicitlyImmutableDataTypes() {
            return implicitlyImmutableDataTypes.getOrElse(null);
        }

        @Override
        public Set<FieldInfo> getEventuallyImmutableFields() {
            return eventuallyImmutableFields.toImmutableSet();
        }

        @Override
        public void transferPropertiesToAnnotations(AnalysisProvider analysisProvider,
                                                    E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {

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
                    annotationChecks.toImmutableMap(),
                    constantObjectFlows.toImmutableSet(),
                    approvedPreconditionsE1.toImmutableMap(),
                    approvedPreconditionsE2.toImmutableMap(),
                    eventuallyImmutableFields.toImmutableSet(),
                    implicitlyImmutableDataTypes.isSet() ? implicitlyImmutableDataTypes.get() : Set.of(),
                    getAspects(),
                    ImmutableList.copyOf(invariants.getOrElse(List.of())));
        }
    }
}
