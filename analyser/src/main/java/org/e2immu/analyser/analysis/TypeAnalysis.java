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

package org.e2immu.analyser.analysis;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.variable.FieldReference;
import org.e2immu.analyser.parser.InspectionProvider;
import org.e2immu.annotation.NotNull;
import org.e2immu.annotation.NotNull1;
import org.e2immu.annotation.Nullable;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface TypeAnalysis extends Analysis {

    @NotNull
    TypeInfo getTypeInfo();

    @NotNull
    Map<FieldReference, Expression> getApprovedPreconditionsE1();

    @NotNull
    Map<FieldReference, Expression> getApprovedPreconditionsE2();

    boolean containsApprovedPreconditionsE2(FieldReference fieldReference);

    boolean approvedPreconditionsE2IsEmpty();

    @NotNull
    Expression getApprovedPreconditions(boolean e2, FieldReference fieldInfo);

    default Map<FieldReference, Expression> getApprovedPreconditions(boolean e2) {
        return e2 ? getApprovedPreconditionsE2() : getApprovedPreconditionsE1();
    }

    @NotNull
    CausesOfDelay approvedPreconditionsStatus(boolean e2, FieldReference fieldInfo);

    @NotNull
    CausesOfDelay approvedPreconditionsStatus(boolean e2);

    @NotNull1
    Set<FieldInfo> getEventuallyImmutableFields();

    @NotNull1
    Set<FieldInfo> getGuardedByEventuallyImmutableFields();

    FieldInfo translateToVisibleField(FieldReference fieldReference);

    default String markLabel() {
        return marksRequiredForImmutable().stream().map(f -> f.name).sorted().collect(Collectors.joining(","));
    }

    default boolean isEventual() {
        return !getApprovedPreconditionsE1().isEmpty() || !getApprovedPreconditionsE2().isEmpty() ||
                !getEventuallyImmutableFields().isEmpty();
    }

    /*
    if value is an eventually immutable field, then the e2 precondition on field value.t will be present
    as well. We don't want them to show up in the annotations.
     */
    default Set<FieldInfo> marksRequiredForImmutable() {
        Set<FieldInfo> res = new HashSet<>(getEventuallyImmutableFields());
        getApprovedPreconditionsE1().keySet().stream()
                .map(this::translateToVisibleField).filter(Objects::nonNull).forEach(res::add);
        getApprovedPreconditionsE2().keySet().stream()
                .map(this::translateToVisibleField).filter(Objects::nonNull).forEach(res::add);
        return res;
    }

    /**
     * Returns the hidden content types
     *
     * @return null when not yet set
     */
    @Nullable
    SetOfTypes getTransparentTypes();

    /**
     * Hidden content types, concretely (type parameters are substituted)
     * <p>
     * IMPROVE probably too simplistic
     *
     * @param concreteType the concrete type used to substitute
     * @return a set of concrete hidden content types
     */
    @Nullable
    default SetOfTypes getTransparentTypes(ParameterizedType concreteType) {
        SetOfTypes transparentTypes = getTransparentTypes();
        if (transparentTypes == null) return null;
        return new SetOfTypes(transparentTypes.types().stream()
                .map(pt -> {
                    if (pt.typeParameter != null) {
                        int index = pt.typeParameter.getIndex();
                        if (index < concreteType.parameters.size()) {
                            return concreteType.parameters.get(index);
                        }
                    }
                    return pt;
                }).collect(Collectors.toUnmodifiableSet()));
    }

    default DV getTypeProperty(Property property) {
        switch (property) {
            case IMMUTABLE, INDEPENDENT, CONTAINER -> {
                DV dv = getPropertyFromMapDelayWhenAbsent(property);
                assert !getTypeInfo().shallowAnalysis() || dv.isDone() : "Shallow analysis must have set a value for " + property + " in " + getTypeInfo();
                return dv;
            }
            case PARTIAL_IMMUTABLE, PARTIAL_CONTAINER -> {
                boolean doNotDelay = getTypeInfo().shallowAnalysis();
                return doNotDelay ? getPropertyFromMapNeverDelay(property)
                        : getPropertyFromMapDelayWhenAbsent(property);
            }
            case EXTENSION_CLASS, UTILITY_CLASS, SINGLETON, FINALIZER -> {
                // ensure that we do not throw an exception
                boolean doNotDelay = getTypeInfo().typePropertiesAreContracted() || getTypeInfo().shallowAnalysis();
                return doNotDelay ? getPropertyFromMapNeverDelay(property)
                        : getPropertyFromMapDelayWhenAbsent(property);
            }
            default -> throw new PropertyException(Analyser.AnalyserIdentification.TYPE, property);
        }
    }

    /**
     * @return the labels of the aspects, pointing to the method on which the aspect has been marked
     */
    @NotNull
    Map<String, MethodInfo> getAspects();

    default boolean aspectsIsSet(String aspect) {
        return getAspects().containsKey(aspect);
    }

    default DV maxValueFromInterfacesImplemented(AnalysisProvider analysisProvider, Property property) {
        Stream<TypeInfo> implementedInterfaces = getTypeInfo().typeResolution.get().superTypesExcludingJavaLangObject()
                .stream().filter(TypeInfo::isInterface);
        return implementedInterfaces.map(analysisProvider::getTypeAnalysis)
                .map(typeAnalysis -> typeAnalysis.getTypeProperty(property))
                .reduce(DV.MIN_INT_DV, DV::max);
    }

    /*
    Optional<T> is @E2Immutable. In general T can be mutable; it is part of the immutable content of Optional.
    Optional<Integer> is @ERImmutable, because Integer is so.
     */
    DV immutableCanBeIncreasedByTypeParameters();

    /*
    too simple an implementation, we should do real bookkeeping: which fields hold which types?
     */
    default Set<ParameterizedType> hiddenContentLinkedTo(FieldInfo fieldInfo) {
        return getTransparentTypes().types();
    }

    Set<ParameterizedType> getExplicitTypes(InspectionProvider inspectionProvider);

    default DV isPartOfHiddenContent(ParameterizedType type) {
        CausesOfDelay status = hiddenContentTypeStatus();
        if (status.isDone()) {
            return DV.fromBoolDv(getTransparentTypes().contains(type.withoutTypeParameters()));
        }
        return status;
    }

    @NotNull
    CausesOfDelay hiddenContentTypeStatus();

    boolean approvedPreconditionsIsNotEmpty(boolean e2);

    default void setAspect(String aspect, MethodInfo mainMethod) {
        throw new UnsupportedOperationException();
    }
}
