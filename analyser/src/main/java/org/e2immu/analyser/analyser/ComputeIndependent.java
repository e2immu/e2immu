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

package org.e2immu.analyser.analyser;

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.NamedType;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;

import java.util.HashSet;
import java.util.Set;

import static org.e2immu.analyser.analyser.LV.*;
import static org.e2immu.analyser.analyser.LinkedVariables.*;

public interface ComputeIndependent {

    DV typeImmutable(ParameterizedType pt);

    /**
     * Variables of two types are linked to each other, at a given <code>linkLevel</code>.
     * <p>
     * Case 1: ComputedParameterAnalyser
     * The first type ('a') is the parameter's (formal) type, the second ('b') is a field.
     * <p>
     * Case 2: ComputingMethodAnalyser
     * The first type ('a') is the concrete return type, the second ('b') is a field
     * <p>
     * Case 3: ComputingFieldAnalyser
     * The first type ('a') is the field's (formal? concrete?) type
     * <p>
     * The link level has not been adjusted to the mutability of the inclusion or intersection type:
     * if a mutable X is part of the hidden content of Set, we use <code>DEPENDENT</code>, rather than <code>COMMON_HC</code>.
     * On the other hand, a link level of DEPENDENT assumes that the underlying type is mutable.
     * A link level of INDEPENDENT should not occur, but for convenience we translate directly. The immutability status
     * of the types is not important in this case.
     * <p>
     * LinkedVariables(target) = { field1:linkLevel1, field2:linkLevel2, ...}
     *
     * @param linkLevel       any of STATICALLY_ASSIGNED, ASSIGNED, DEPENDENT, COMMON_HC, INDEPENDENT
     * @param a               one of the two types
     * @param immutableAInput not null if you already know the immutable value of <code>a</code>
     * @param b               one of the two types, can be equal to <code>a</code>
     * @return the value for the INDEPENDENT property
     */
    DV typesAtLinkLevel(LV linkLevel, ParameterizedType a, DV immutableAInput, ParameterizedType b);

    DV hasHiddenContentIntersection(ParameterizedType a1, ParameterizedType a2);
}
