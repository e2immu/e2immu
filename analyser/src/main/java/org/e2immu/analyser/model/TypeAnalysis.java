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

package org.e2immu.analyser.model;

import org.e2immu.analyser.analyser.Analyser;
import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.PropertyException;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.variable.FieldReference;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public interface TypeAnalysis extends Analysis {

    TypeInfo getTypeInfo();

    Map<FieldReference, Expression> getApprovedPreconditionsE1();

    Map<FieldReference, Expression> getApprovedPreconditionsE2();

    boolean containsApprovedPreconditionsE2(FieldReference fieldReference);

    boolean approvedPreconditionsE2IsEmpty();

    Expression getApprovedPreconditions(boolean e2, FieldReference fieldInfo);

    default Map<FieldReference, Expression> getApprovedPreconditions(boolean e2) {
        return e2 ? getApprovedPreconditionsE2() : getApprovedPreconditionsE1();
    }

    boolean approvedPreconditionsIsSet(boolean e2, FieldReference fieldInfo);

    boolean approvedPreconditionsIsFrozen(boolean e2);

    Set<FieldInfo> getEventuallyImmutableFields();

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
        getApprovedPreconditionsE1().keySet().stream().map(this::translateToVisibleField).forEach(res::add);
        getApprovedPreconditionsE2().keySet().stream().map(this::translateToVisibleField).forEach(res::add);
        return res;
    }

    /**
     * @return null when not yet set
     */
    Set<ParameterizedType> getTransparentTypes();

    default int getTypeProperty(VariableProperty variableProperty) {
        boolean doNotDelay = getTypeInfo().typePropertiesAreContracted() || getTypeInfo().shallowAnalysis();

        switch (variableProperty) {
            case IMMUTABLE, CONTAINER, EXTENSION_CLASS, UTILITY_CLASS, SINGLETON, FINALIZER, INDEPENDENT -> {
                // ensure that we do not throw an exception
            }
            default -> throw new PropertyException(Analyser.AnalyserIdentification.TYPE, variableProperty);
        }
        return doNotDelay ? getPropertyFromMapNeverDelay(variableProperty)
                : getPropertyFromMapDelayWhenAbsent(variableProperty);
    }

    /**
     * @return the labels of the aspects, pointing to the method on which the aspect has been marked
     */
    Map<String, MethodInfo> getAspects();

    default boolean aspectsIsSet(String aspect) {
        return getAspects().containsKey(aspect);
    }

    default int maxValueFromInterfacesImplemented(AnalysisProvider analysisProvider, VariableProperty variableProperty) {
        Stream<TypeInfo> implementedInterfaces = getTypeInfo().typeResolution.get().superTypesExcludingJavaLangObject()
                .stream().filter(TypeInfo::isInterface);
        return implementedInterfaces.map(analysisProvider::getTypeAnalysis)
                .mapToInt(typeAnalysis -> typeAnalysis.getTypeProperty(variableProperty))
                .max().orElse(Level.DELAY);
    }
}
