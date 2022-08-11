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

    boolean approvedPreconditionsIsNotEmpty(boolean e2);

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

    default void setAspect(String aspect, MethodInfo mainMethod) {
        throw new UnsupportedOperationException();
    }

    /* ******* methods dealing with explicit and transparent types in the object graph of the fields *************** */

    /*
    Optional<T> is @E2Immutable. In general T can be mutable; it is part of the hidden content of Optional.
    Optional<Integer> is @ERImmutable, because Integer is so.
     */
    DV immutableCanBeIncreasedByTypeParameters();

    /**
     * Returns the transparent types: types in the object graph of the fields that are never accessed.
     * Any unbound type parameter is always transparent as a type in the object graph.
     * Ensure that none of 'this' and 'super' types are transparent!
     *
     * @return null when not yet set, use transparentAndExplicitTypeComputationDelays to check
     */
    @Nullable
    SetOfTypes getTransparentTypes();

    /**
     * The explicit types are those types in the object graph of the fields that are accessed.
     * By definition, the sets of explicit types and transparent types are disjoint.
     *
     * @return null when not yet set, use transparentAndExplicitTypeComputationDelays to check
     */
    SetOfTypes getExplicitTypes(InspectionProvider inspectionProvider);

    /**
     * The hidden content of a type as computed. Contains all the transparent types, and those
     * explicit types that are at least level 2 immutable, but not recursively immutable.
     * As a result, this set does not contain any primitives or JLO.
     * By convention, neither does it contain the type itself, nor any of the types in its hierarchy.
     *
     * @return null when not yet set, use transparentAndExplicitTypeComputationDelays to check
     */
    SetOfTypes getHiddenContentTypes();

    /**
     * Helper method, but does not do the whole job
     *
     * @param type is this type transparent? look at implementations on how to trim it down
     * @return a delay when the computation is not done yet
     */
    default DV isTransparent(ParameterizedType type) {
        CausesOfDelay status = transparentAndExplicitTypeComputationDelays();
        if (status.isDone()) {
            return DV.fromBoolDv(getTransparentTypes().contains(type.withoutTypeParameters()));
        }
        return status;
    }

    @NotNull
    CausesOfDelay transparentAndExplicitTypeComputationDelays();
}
