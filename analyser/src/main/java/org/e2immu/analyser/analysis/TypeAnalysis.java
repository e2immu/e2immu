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
import org.e2immu.annotation.NotNull;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public interface TypeAnalysis extends Analysis {

    @NotNull
    TypeInfo getTypeInfo();

    @NotNull
    Map<FieldReference, Expression> getApprovedPreconditionsFinalFields();

    @NotNull
    Map<FieldReference, Expression> getApprovedPreconditionsImmutable();

    boolean containsApprovedPreconditionsImmutable(FieldReference fieldReference);

    boolean approvedPreconditionsImmutableIsEmpty();

    @NotNull
    Expression getApprovedPreconditions(boolean e2, FieldReference fieldInfo);

    default Map<FieldReference, Expression> getApprovedPreconditions(boolean e2) {
        return e2 ? getApprovedPreconditionsImmutable() : getApprovedPreconditionsFinalFields();
    }

    @NotNull
    CausesOfDelay approvedPreconditionsStatus(boolean e2, FieldReference fieldInfo);

    @NotNull
    CausesOfDelay approvedPreconditionsStatus(boolean e2);

    boolean approvedPreconditionsIsNotEmpty(boolean e2);

    @NotNull(content = true)
    Set<FieldInfo> getEventuallyImmutableFields();

    @NotNull(content = true)
    Set<FieldInfo> getGuardedByEventuallyImmutableFields();

    FieldInfo translateToVisibleField(FieldReference fieldReference);

    default String markLabel() {
        return marksRequiredForImmutable().stream().map(f -> f.name).sorted().collect(Collectors.joining(","));
    }

    default boolean isEventual() {
        return !getApprovedPreconditionsFinalFields().isEmpty() || !getApprovedPreconditionsImmutable().isEmpty() ||
                !getEventuallyImmutableFields().isEmpty();
    }

    /*
    if value is an eventually immutable field, then the e2 precondition on field value.t will be present
    as well. We don't want them to show up in the annotations.
     */
    default Set<FieldInfo> marksRequiredForImmutable() {
        Set<FieldInfo> res = new HashSet<>(getEventuallyImmutableFields());
        getApprovedPreconditionsFinalFields().keySet().stream()
                .map(this::translateToVisibleField).filter(Objects::nonNull).forEach(res::add);
        getApprovedPreconditionsImmutable().keySet().stream()
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
            case EXTENSION_CLASS, UTILITY_CLASS, SINGLETON, FINALIZER, MODIFIED_METHOD -> {
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

    /* ******* methods dealing with explicit and hidden content types in the object graph of the fields *************** */

    /*
    Optional<T> is @Immutable with hidden content of type T.
    Optional<Integer> is @Immutable without hidden content, because Integer has these immutability properties.
    Optional<StringBuilder> is not immutable, because StringBuilder is not.

    This method returns DV.TRUE when the immutable property varies with its type parameters,
    DV.FALSE when not, and a delay when we don't know yet.

    IMPROVE at some point, extend to "immutableDeterminedByCasts"? See Basics_20.C3, where we use Object
    rather than a type parameter.
     */
    DV immutableDeterminedByTypeParameters();

    /**
     * The hidden content of a type as computed. Contains all the types of the fields
     * that are immutable, but not recursively immutable.
     * As a result, this set does not contain any primitives, or java.lang.String, for example.
     * By convention, neither does it contain the type itself, nor any of the types in its hierarchy.
     *
     * @return null when not yet set, use hiddenContentAndExplicitTypeComputationDelays to check
     */
    SetOfTypes getHiddenContentTypes();

    @NotNull
    CausesOfDelay hiddenContentDelays();
}
