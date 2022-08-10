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

package org.e2immu.analyser.analysis.impl;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.expression.VariableExpression;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.E2ImmuAnnotationExpressions;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.analyser.parser.Primitives;
import org.e2immu.support.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class TypeAnalysisImpl extends AnalysisImpl implements TypeAnalysis {
    private static final Logger LOGGER = LoggerFactory.getLogger(TypeAnalysisImpl.class);

    private final TypeInfo typeInfo;
    private final Map<FieldReference, Expression> approvedPreconditionsE1;
    private final Map<FieldReference, Expression> approvedPreconditionsE2;

    private final SetOfTypes hiddenContentTypes;
    private final Map<String, MethodInfo> aspects;
    private final Set<FieldInfo> eventuallyImmutableFields;
    private final Set<FieldInfo> guardedByEventuallyImmutableFields;
    private final Set<FieldInfo> visibleFields;

    private final boolean immutableCanBeIncreasedByTypeParameters;

    private TypeAnalysisImpl(TypeInfo typeInfo,
                             Map<Property, DV> properties,
                             Map<AnnotationExpression, AnnotationCheck> annotations,
                             Map<FieldReference, Expression> approvedPreconditionsE1,
                             Map<FieldReference, Expression> approvedPreconditionsE2,
                             Set<FieldInfo> eventuallyImmutableFields,
                             Set<FieldInfo> guardedByEventuallyImmutableFields,
                             SetOfTypes hiddenContentTypes,
                             Map<String, MethodInfo> aspects,
                             Set<FieldInfo> visibleFields,
                             boolean immutableCanBeIncreasedByTypeParameters) {
        super(properties, annotations);
        this.typeInfo = typeInfo;
        this.approvedPreconditionsE1 = approvedPreconditionsE1;
        this.approvedPreconditionsE2 = approvedPreconditionsE2;
        this.hiddenContentTypes = hiddenContentTypes;
        this.aspects = Objects.requireNonNull(aspects);
        this.eventuallyImmutableFields = eventuallyImmutableFields;
        this.guardedByEventuallyImmutableFields = guardedByEventuallyImmutableFields;
        this.visibleFields = visibleFields;
        this.immutableCanBeIncreasedByTypeParameters = immutableCanBeIncreasedByTypeParameters;
    }

    @Override
    public DV immutableCanBeIncreasedByTypeParameters() {
        return DV.fromBoolDv(immutableCanBeIncreasedByTypeParameters);
    }

    @Override
    public TypeInfo getTypeInfo() {
        return typeInfo;
    }

    @Override
    public boolean approvedPreconditionsE2IsEmpty() {
        return approvedPreconditionsE2.isEmpty();
    }

    @Override
    public boolean containsApprovedPreconditionsE2(FieldReference fieldReference) {
        return approvedPreconditionsE2.containsKey(fieldReference);
    }

    @Override
    public Set<FieldInfo> getEventuallyImmutableFields() {
        return eventuallyImmutableFields;
    }

    @Override
    public Set<FieldInfo> getGuardedByEventuallyImmutableFields() {
        return guardedByEventuallyImmutableFields;
    }

    @Override
    public Map<String, MethodInfo> getAspects() {
        return aspects;
    }

    @Override
    public DV getProperty(Property property) {
        return getTypeProperty(property);
    }

    @Override
    public Location location(Stage stage) {
        return typeInfo.newLocation();
    }

    @Override
    public Map<FieldReference, Expression> getApprovedPreconditionsE1() {
        return approvedPreconditionsE1;
    }

    @Override
    public Map<FieldReference, Expression> getApprovedPreconditionsE2() {
        return approvedPreconditionsE2;
    }

    @Override
    public Expression getApprovedPreconditions(boolean e2, FieldReference fieldReference) {
        return e2 ? approvedPreconditionsE2.get(fieldReference) : approvedPreconditionsE1.get(fieldReference);
    }

    @Override
    public CausesOfDelay approvedPreconditionsStatus(boolean e2, FieldReference fieldInfo) {
        return CausesOfDelay.EMPTY;
    }

    @Override
    public CausesOfDelay approvedPreconditionsStatus(boolean e2) {
        return CausesOfDelay.EMPTY;
    }

    @Override
    public SetOfTypes getTransparentTypes() {
        return hiddenContentTypes;
    }

    @Override
    public FieldInfo translateToVisibleField(FieldReference fieldReference) {
        return translateToVisibleField(visibleFields, fieldReference);
    }

    // AnnotatedAPI situation. We simply collect the types visible in the API.
    @Override
    public SetOfTypes getExplicitTypes(InspectionProvider inspectionProvider) {
        return typeInfo.typeInspection.get().typesOfMethodsAndConstructors(InspectionProvider.DEFAULT);
    }

    @Override
    public CausesOfDelay transparentAndExplicitTypeComputationDelays() {
        return CausesOfDelay.EMPTY;
    }

    @Override
    public boolean approvedPreconditionsIsNotEmpty(boolean e2) {
        return e2 ? !approvedPreconditionsE2.isEmpty() : !approvedPreconditionsE1.isEmpty();
    }

    public static class CycleInfo {
        public final AddOnceSet<MethodInfo> nonModified = new AddOnceSet<>();
        public final FlipSwitch modified = new FlipSwitch();

        @Override
        public String toString() {
            return "{" + nonModified.stream().map(m -> m.name).sorted().collect(Collectors.joining(",")) + (modified.isSet() ? "_M" : "") + "}";
        }
    }

    static FieldInfo translateToVisibleField(Set<FieldInfo> visibleFields, FieldReference fieldReference) {
        if (visibleFields.contains(fieldReference.fieldInfo)) return fieldReference.fieldInfo;
        if (fieldReference.scope instanceof VariableExpression ve && ve.variable() instanceof FieldReference fr) {
            return translateToVisibleField(visibleFields, fr);
        }
        return null; // not one of ours, i
    }

    public static class Builder extends AbstractAnalysisBuilder implements TypeAnalysis {
        public final TypeInfo typeInfo;

        // from label to condition BEFORE (used by @Mark and @Only(before="label"))
        private final SetOnceMap<FieldReference, Expression> approvedPreconditionsE1 = new SetOnceMap<>();
        private final SetOnceMap<FieldReference, Expression> approvedPreconditionsE2 = new SetOnceMap<>();
        private final AddOnceSet<FieldInfo> eventuallyImmutableFields = new AddOnceSet<>();
        private final AddOnceSet<FieldInfo> guardedByEventuallyImmutableFields = new AddOnceSet<>();

        private final VariableFirstThen<CausesOfDelay, SetOfTypes> hiddenContentTypes;
        private final SetOnce<SetOfTypes> explicitTypes = new SetOnce<>();

        public final SetOnceMap<String, MethodInfo> aspects = new SetOnceMap<>();

        public final SetOnceMap<Set<MethodInfo>, CycleInfo> nonModifiedCountForMethodCallCycle = new SetOnceMap<>();
        public final SetOnce<Boolean> ignorePrivateConstructorsForFieldValues = new SetOnce<>();

        private final Set<FieldInfo> visibleFields;
        public final AnalysisMode analysisMode;

        private final VariableFirstThen<CausesOfDelay, Boolean> immutableCanBeIncreasedByTypeParameters;

        private CausesOfDelay approvedPreconditionsE1Delays;
        private CausesOfDelay approvedPreconditionsE2Delays;


        @Override
        public void internalAllDoneCheck() {
            super.internalAllDoneCheck();
            assert approvedPreconditionsE2.isFrozen();
            assert approvedPreconditionsE1.isFrozen();
            assert immutableCanBeIncreasedByTypeParameters.isSet();
            assert hiddenContentTypes.isSet();
            assert explicitTypes.isSet();
        }

        private static CausesOfDelay initialDelay(TypeInfo typeInfo) {
            return typeInfo.delay(CauseOfDelay.Cause.INITIAL_VALUE);
        }

        /*
        analyser context can be null for Primitives, ShallowTypeAnalyser
         */
        public Builder(AnalysisMode analysisMode, Primitives primitives, TypeInfo typeInfo, AnalyserContext analyserContext) {
            super(primitives, typeInfo.simpleName);
            this.typeInfo = typeInfo;
            this.analysisMode = analysisMode;
            this.visibleFields = analyserContext == null ? Set.of() : Set.copyOf(typeInfo.visibleFields(analyserContext));
            CausesOfDelay initialDelay = initialDelay(typeInfo);
            hiddenContentTypes = new VariableFirstThen<>(initialDelay);
            immutableCanBeIncreasedByTypeParameters = new VariableFirstThen<>(initialDelay);
            approvedPreconditionsE2Delays = initialDelay;
            approvedPreconditionsE1Delays = initialDelay;
        }

        @Override
        protected void writeTypeEventualFields(String after) {
            for (String fieldName : after.split(",")) {
                FieldInfo fieldInfo = getTypeInfo().getFieldByName(fieldName.trim(), false);
                if (fieldInfo != null) {
                    eventuallyImmutableFields.add(fieldInfo);
                } else {
                    LOGGER.warn("Could not find field {} in type {}, is supposed to be eventual", fieldName,
                            typeInfo.fullyQualifiedName);
                }
            }
        }

        @Override
        public void setAspect(String aspect, MethodInfo mainMethod) {
            aspects.put(aspect, mainMethod);
        }

        @Override
        public TypeInfo getTypeInfo() {
            return typeInfo;
        }

        @Override
        public AnalysisMode analysisMode() {
            return analysisMode;
        }

        @Override
        public boolean approvedPreconditionsE2IsEmpty() {
            return approvedPreconditionsE2.isEmpty();
        }

        @Override
        public boolean containsApprovedPreconditionsE2(FieldReference fieldReference) {
            return approvedPreconditionsE2.isSet(fieldReference);
        }

        @Override
        public FieldInfo translateToVisibleField(FieldReference fieldReference) {
            return TypeAnalysisImpl.translateToVisibleField(visibleFields, fieldReference);
        }

        @Override
        public Expression getApprovedPreconditions(boolean e2, FieldReference fieldReference) {
            return e2 ? approvedPreconditionsE2.get(fieldReference) : approvedPreconditionsE1.get(fieldReference);
        }

        @Override
        public CausesOfDelay approvedPreconditionsStatus(boolean e2, FieldReference fieldReference) {
            assert fieldReference != null;
            return e2 ? (approvedPreconditionsE2.isSet(fieldReference) ? CausesOfDelay.EMPTY :
                    fieldReference.fieldInfo.delay(CauseOfDelay.Cause.APPROVED_PRECONDITIONS)) :
                    (approvedPreconditionsE1.isSet(fieldReference) ? CausesOfDelay.EMPTY :
                            fieldReference.fieldInfo.delay(CauseOfDelay.Cause.APPROVED_PRECONDITIONS));
        }

        @Override
        public CausesOfDelay approvedPreconditionsStatus(boolean e2) {
            return e2 ? (approvedPreconditionsE2.isFrozen() ? CausesOfDelay.EMPTY : approvedPreconditionsE2Delays)
                    : (approvedPreconditionsE1.isFrozen() ? CausesOfDelay.EMPTY : approvedPreconditionsE1Delays);
        }

        public void freezeApprovedPreconditionsE1() {
            approvedPreconditionsE1.freeze();
        }

        @Override
        public boolean approvedPreconditionsIsNotEmpty(boolean e2) {
            return e2 ? !approvedPreconditionsE2.isEmpty() : !approvedPreconditionsE1.isEmpty();
        }

        public void putInApprovedPreconditionsE1(FieldReference fieldReference, Expression expression) {
            assert fieldReference != null;
            assert expression != null;
            approvedPreconditionsE1.put(fieldReference, expression);
        }

        public void freezeApprovedPreconditionsE2() {
            approvedPreconditionsE2.freeze();
        }

        public void putInApprovedPreconditionsE2(FieldReference fieldReference, Expression expression) {
            assert fieldReference != null;
            assert expression != null;
            approvedPreconditionsE2.put(fieldReference, expression);
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
        public DV getProperty(Property property) {
            return getTypeProperty(property);
        }

        @Override
        public Location location(Stage stage) {
            return typeInfo.newLocation();
        }

        @Override
        public Map<FieldReference, Expression> getApprovedPreconditionsE1() {
            return approvedPreconditionsE1.toImmutableMap();
        }

        @Override
        public Map<FieldReference, Expression> getApprovedPreconditionsE2() {
            return approvedPreconditionsE2.toImmutableMap();
        }

        @Override
        public SetOfTypes getTransparentTypes() {
            return hiddenContentTypes.get();
        }

        @Override
        public Set<FieldInfo> getEventuallyImmutableFields() {
            return eventuallyImmutableFields.toImmutableSet();
        }

        @Override
        public Set<FieldInfo> getGuardedByEventuallyImmutableFields() {
            return guardedByEventuallyImmutableFields.toImmutableSet();
        }

        public void transferPropertiesToAnnotations(E2ImmuAnnotationExpressions e2ImmuAnnotationExpressions) {

            // @ExtensionClass
            if (getProperty(Property.EXTENSION_CLASS).valueIsTrue()) {
                annotations.put(e2ImmuAnnotationExpressions.extensionClass, true);
            }

            // @Finalizer
            if (getProperty(Property.FINALIZER).valueIsTrue()) {
                annotations.put(e2ImmuAnnotationExpressions.finalizer, true);
            }

            // @UtilityClass
            if (getProperty(Property.UTILITY_CLASS).valueIsTrue()) {
                annotations.put(e2ImmuAnnotationExpressions.utilityClass, true);
            }

            // @Singleton
            if (getProperty(Property.SINGLETON).valueIsTrue()) {
                annotations.put(e2ImmuAnnotationExpressions.singleton, true);
            }

            DV immutable = getProperty(Property.IMMUTABLE);
            DV container = getProperty(Property.CONTAINER);
            doImmutableContainer(e2ImmuAnnotationExpressions, immutable, container, false);

            // @Independent
            DV independent = getProperty(Property.INDEPENDENT);
            doIndependent(e2ImmuAnnotationExpressions, independent, MultiLevel.NOT_INVOLVED_DV, immutable);
        }

        // this implementation is a bit of a hack -- explicit types are not set directly for shallowly
        // analysed types. When extending from a shallow type, e.g., when extending from RuntimeException,
        // you'll need its explicit types. They may reach further than what we've investigated, so
        // those get filtered out. See e.g. Enum_0, which extends implicitly from java.lang.Enum
        @Override
        public SetOfTypes getExplicitTypes(InspectionProvider inspectionProvider) {
            if (!explicitTypes.isSet()) {
                if (getTypeInfo().shallowAnalysis()) {
                    SetOfTypes set = typeInfo.typeInspection.get().typesOfMethodsAndConstructors(InspectionProvider.DEFAULT);
                    Set<ParameterizedType> filtered = set.types().stream()
                            .filter(pt -> pt.typeInfo != null && pt.typeInfo.typeInspection.isSet())
                            .collect(Collectors.toUnmodifiableSet());
                    explicitTypes.set(new SetOfTypes(filtered));
                    return new SetOfTypes(filtered);
                }
                return null; // not yet set
            }
            return explicitTypes.get(typeInfo.fullyQualifiedName);
        }

        public void setExplicitTypes(SetOfTypes explicitTypes) {
            this.explicitTypes.set(explicitTypes);
        }

        public void copyExplicitTypes(Builder other) {
            this.explicitTypes.copy(other.explicitTypes);
        }

        @Override
        public CausesOfDelay transparentAndExplicitTypeComputationDelays() {
            if (hiddenContentTypes.isSet()) return CausesOfDelay.EMPTY;
            return hiddenContentTypes.getFirst();
        }

        @Override
        public DV immutableCanBeIncreasedByTypeParameters() {
            return immutableCanBeIncreasedByTypeParameters.isFirst() ? immutableCanBeIncreasedByTypeParameters.getFirst()
                    : DV.fromBoolDv(immutableCanBeIncreasedByTypeParameters.get());
        }

        public void setImmutableCanBeIncreasedByTypeParameters(CausesOfDelay causes) {
            immutableCanBeIncreasedByTypeParameters.setFirst(causes);
        }

        public void setImmutableCanBeIncreasedByTypeParameters(Boolean b) {
            immutableCanBeIncreasedByTypeParameters.set(b);
        }

        public void setTransparentTypes(SetOfTypes setOfTypes) {
            hiddenContentTypes.set(setOfTypes);
        }

        public void setHiddenContentTypesDelay(CausesOfDelay causes) {
            hiddenContentTypes.setFirst(causes);
        }

        public TypeAnalysis build() {
            return new TypeAnalysisImpl(typeInfo,
                    properties.toImmutableMap(),
                    annotationChecks.toImmutableMap(),
                    approvedPreconditionsE1.toImmutableMap(),
                    approvedPreconditionsE2.toImmutableMap(),
                    eventuallyImmutableFields.toImmutableSet(),
                    guardedByEventuallyImmutableFields.toImmutableSet(),
                    hiddenContentTypes.isSet() ? hiddenContentTypes.get() : SetOfTypes.EMPTY,
                    getAspects(),
                    visibleFields,
                    immutableCanBeIncreasedByTypeParameters.getOrDefault(false));
        }

        public void setApprovedPreconditionsE1Delays(CausesOfDelay causes) {
            approvedPreconditionsE1Delays = causes;
        }

        public void setApprovedPreconditionsE2Delays(CausesOfDelay causes) {
            approvedPreconditionsE2Delays = causes;
        }

        private static final Set<Property> ACCEPTED = Set.of(Property.IMMUTABLE, Property.PARTIAL_IMMUTABLE,
                Property.CONTAINER, Property.PARTIAL_CONTAINER, Property.FINALIZER,
                Property.INDEPENDENT, Property.EXTENSION_CLASS, Property.UTILITY_CLASS, Property.SINGLETON);

        @Override
        public void setProperty(Property property, DV i) {
            assert ACCEPTED.contains(property) : "Do not accept " + property + " on types";
            super.setProperty(property, i);
        }

        public Set<FieldInfo> nonFinalFieldsNotApprovedOrGuarded(List<FieldReference> nonFinalFields) {
            return nonFinalFields.stream()
                    .filter(fr -> !approvedPreconditionsE1.isSet(fr) && !guardedByEventuallyImmutableFields.contains(fr.fieldInfo))
                    .map(fr -> fr.fieldInfo)
                    .collect(Collectors.toUnmodifiableSet());
        }

        public boolean eventuallyImmutableFieldNotYetSet(FieldInfo fieldInfo) {
            return !eventuallyImmutableFields.contains(fieldInfo);
        }

        public void addEventuallyImmutableField(FieldInfo fieldInfo) {
            assert fieldInfo != null;
            eventuallyImmutableFields.add(fieldInfo);
        }

        @Override
        public String markLabelFromType() {
            return isEventual() ? markLabel() : "";
        }

        public void addGuardedByEventuallyImmutableField(FieldInfo fieldInfo) {
            assert fieldInfo != null;
            if (!guardedByEventuallyImmutableFields.contains(fieldInfo)) {
                guardedByEventuallyImmutableFields.add(fieldInfo);
            }
        }
    }
}
