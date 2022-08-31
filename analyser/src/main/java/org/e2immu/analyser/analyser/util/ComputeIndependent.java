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

package org.e2immu.analyser.analyser.util;

import org.e2immu.analyser.analyser.AnalyserContext;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.LinkedVariables;
import org.e2immu.analyser.analyser.SetOfTypes;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.ParameterizedType;
import org.e2immu.analyser.model.TypeInfo;

import static org.e2immu.analyser.analyser.LinkedVariables.*;

public record ComputeIndependent(AnalyserContext analyserContext, TypeInfo currentPrimaryType) {

    public ComputeIndependent {
        assert analyserContext != null;
        assert currentPrimaryType != null;
    }

    /**
     * Variables of two types are linked to each other, at a given <code>linkLevel</code>.
     * <p>
     * Case 1: {@link org.e2immu.analyser.analyser.impl.ComputedParameterAnalyser}
     * The first type ('a') is the parameter's (formal) type, the second ('b') is a field.
     * <p>
     * Case 2: {@link org.e2immu.analyser.analyser.impl.ComputingMethodAnalyser}
     * The first type ('a') is the concrete return type, the second ('b') is a field
     * <p>
     * Case 3: {@link org.e2immu.analyser.analyser.impl.FieldAnalyserImpl}
     * The first type ('a') is the field's (formal? concrete?) type
     * <p>
     * The link-level has already been adapted to the immutability status of the two types: if
     * 'a' is immutable, it should be with hidden content, because otherwise there would be no link.
     * A type parameter cannot have link-level DEPENDENT.
     * <p>
     * Examples:
     * <p>
     * A parameter of type E linked to a field of List type can be either assigned, or IN_HC_OF.
     * Similarly, a parameter of type Object linked to a field of List type can be either assigned, or IN_HC_OF.
     * A parameter of type Set linked to a field of List type must be DEPENDENT or COMMON_HC, as they're not assignable.
     *
     * @param linkLevel  any of STATICALLY_ASSIGNED, ASSIGNED, DEPENDENT, IN_HC_OF, COMMON_HC, INDEPENDENT
     * @param a          one of the two types
     * @param immutableA not null if you already know the immutable value of <code>a</code>
     * @param b          one of the two types, can be equal to <code>a</code>
     * @return the value for the INDEPENDENT property
     */
    public DV compute(DV linkLevel, ParameterizedType a, DV immutableA, ParameterizedType b) {
        assert linkLevel.isDone();
        if (LINK_STATICALLY_ASSIGNED.equals(linkLevel) || LINK_ASSIGNED.equals(linkLevel)) {
            /*
             Types a and b are either equal or assignable, otherwise one cannot assign.
             The type relation cannot be IN_HC_OF, it should be COMMON_HC (or INDEPENDENT in case of no information).
             We return the independent value corresponding to the most specific type.
             */
            ParameterizedType mostSpecific = a.mostSpecific(analyserContext, currentPrimaryType, b);
            DV immutable = mostSpecific == a ? immutableA : analyserContext.typeImmutable(b);
            return MultiLevel.independentCorrespondingToImmutable(immutable);
        }
        if (LINK_INDEPENDENT.equals(linkLevel)) return MultiLevel.INDEPENDENT_DV;
        if (LINK_DEPENDENT.equals(linkLevel)) return MultiLevel.DEPENDENT_DV;
        assert LINK_IS_HC_OF.equals(linkLevel) || LINK_COMMON_HC.equals(linkLevel);
        return MultiLevel.INDEPENDENT_HC_DV;
    }


    // FIXME write test for Collection<? extends E>, List<E> etc., occurs in Basics_20

    /**
     * How do the types relate wrt to hidden content?
     *
     * @param pt1 the first type
     * @param pt2 the second type, there is no order, you can exchange pt1 and pt2
     * @return either IS_HC_OF, COMMON_HC, or INDEPENDENT
     */
    public DV typeRelation(ParameterizedType pt1, ParameterizedType pt2) {
        TypeInfo b1 = pt1.bestTypeInfo();
        TypeInfo b2 = pt2.bestTypeInfo();
        if (b1 == null && b2 == null) {
            // two unbound type parameters
            assert pt1.equals(pt2) : "curious to see when this happens";
            return LinkedVariables.LINK_COMMON_HC;
        }
        if (b1 == null) {
            return unboundTypeParameter(pt1, pt2, b2);
        }
        if (b2 == null) {
            return unboundTypeParameter(pt2, pt1, b1);
        }
        if (!pt1.equals(pt2)) {
            TypeAnalysis t1 = analyserContext.getTypeAnalysis(b1);
            if (t1 == null) return LinkedVariables.LINK_INDEPENDENT;
            if (t1.hiddenContentAndExplicitTypeComputationDelays().isDelayed()) {
                return t1.hiddenContentAndExplicitTypeComputationDelays();
            }
            SetOfTypes translatedHcOfT1 = t1.getHiddenContentTypes().translate(analyserContext, pt2);
            if (translatedHcOfT1.contains(pt2)) return LinkedVariables.LINK_IS_HC_OF;

            TypeAnalysis t2 = analyserContext.getTypeAnalysis(b2);
            if (t2 == null) return LinkedVariables.LINK_INDEPENDENT;
            if (t2.hiddenContentAndExplicitTypeComputationDelays().isDelayed()) {
                return t2.hiddenContentAndExplicitTypeComputationDelays();
            }
            SetOfTypes translatedHcOfT2 = t2.getHiddenContentTypes().translate(analyserContext, pt1);
            if (translatedHcOfT2.contains(pt1)) return LinkedVariables.LINK_IS_HC_OF;
        }
        return LinkedVariables.LINK_COMMON_HC;
    }

    /*
    list.add(x), with E the formal type pt1, and X the mutable type x;

    the link level
     */
    private DV unboundTypeParameter(ParameterizedType pt1, ParameterizedType pt2, TypeInfo b2) {
        TypeAnalysis t2 = analyserContext.getTypeAnalysisNullWhenAbsent(b2);
        if (t2 == null) return LinkedVariables.LINK_INDEPENDENT;
        if (t2.hiddenContentAndExplicitTypeComputationDelays().isDelayed()) {
            return t2.hiddenContentAndExplicitTypeComputationDelays();
        }
        if (t2.getHiddenContentTypes().translate(analyserContext, pt2).contains(pt1)) {
            return LinkedVariables.LINK_IS_HC_OF;
        }
        throw new UnsupportedOperationException();
    }
}
