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

package org.e2immu.analyser.parser.conditional.testexample;


import java.util.List;

public interface Precondition_10 {
    record TypeInfo(String name) {
    }

    interface Property {

    }

    Property IMMUTABLE = new Property() {
    };

    interface TypeAnalysis {
        DV getProperty(Property property);

        DV immutableCanBeIncreasedByTypeParameters();
    }

    int HIGH = 1;

    enum Cause {
        TYPE_ANALYSIS(1), C1(0), C2(HIGH), C3(0);

        Cause(int priority) {
            this.priority = priority;
        }

        private int priority;

        public int getPriority() {
            return priority;
        }
    }

    static boolean highPriority(Cause cause) {
        return cause.getPriority() == HIGH;
    }

    record DV(int value, List<Cause> causes) {
        boolean isDone() {
            return value >= 0;
        }

        boolean isDelayed() {
            return value < 0;
        }

        DV max(DV other) {
            return value >= other.value ? this : other;
        }

        DV min(DV other) {
            return value <= other.value ? this : other;
        }

        boolean valueIsTrue() {
            return value == 1;
        }

        boolean containsCauseOfDelay(Cause cause) {
            assert highPriority(cause);
            return causes.stream().anyMatch(c -> c == cause);
        }
    }

    record ParameterizedType(int arrays, TypeInfo bestTypeInfo, List<ParameterizedType> parameters) {
    }

    DV NOT_INVOLVED_DV = new DV(1, List.of());
    DV MUTABLE_DV = new DV(1, List.of(Cause.C1));
    DV EFFECTIVELY_E1IMMUTABLE_DV = new DV(5, List.of(Cause.C1));
    DV EFFECTIVELY_E2IMMUTABLE_DV = new DV(13, List.of(Cause.C2));
    DV EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV = new DV(805, List.of(Cause.C1, Cause.C3));

    default DV defaultImmutable(ParameterizedType parameterizedType, boolean unboundIsMutable) {
        return defaultImmutable(parameterizedType, unboundIsMutable, NOT_INVOLVED_DV);
    }

    default DV defaultImmutable(ParameterizedType parameterizedType, boolean unboundIsMutable, DV dynamicValue) {
        assert dynamicValue.isDone();
        if (parameterizedType.arrays > 0) {
            return EFFECTIVELY_E1IMMUTABLE_DV;
        }
        TypeInfo bestType = parameterizedType.bestTypeInfo();
        if (bestType == null) {
            // unbound type parameter, null constant
            return dynamicValue.max(unboundIsMutable ? NOT_INVOLVED_DV : EFFECTIVELY_E2IMMUTABLE_DV);
        }
        TypeAnalysis typeAnalysis = getTypeAnalysisNullWhenAbsent(bestType);
        DV baseValue = typeAnalysis.getProperty(IMMUTABLE);
        if (baseValue.isDelayed()) {
            return baseValue;
        }
        DV dynamicBaseValue = dynamicValue.max(baseValue);
        if (isAtLeastE2Immutable(dynamicBaseValue) && !parameterizedType.parameters.isEmpty()) {
            DV doSum = typeAnalysis.immutableCanBeIncreasedByTypeParameters();
            if (doSum.isDelayed()) {
                return doSum;
            }
            if (doSum.valueIsTrue()) {
                DV paramValue = parameterizedType.parameters.stream()
                        .map(pt -> defaultImmutable(pt, true)) // FIXME add parameter here, and there is no delay loop
                        .map(v -> v.containsCauseOfDelay(Cause.TYPE_ANALYSIS) ? MUTABLE_DV : v)
                        .reduce(EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, DV::min);
                if (paramValue.isDelayed()) return paramValue;
                return sumImmutableLevels(dynamicBaseValue, paramValue);
            }
        }
        return dynamicBaseValue;
    }

    TypeAnalysis getTypeAnalysisNullWhenAbsent(TypeInfo bestType);

    boolean isAtLeastE2Immutable(DV dynamicBaseValue);

    DV sumImmutableLevels(DV dynamicBaseValue, DV paramValue);

}
