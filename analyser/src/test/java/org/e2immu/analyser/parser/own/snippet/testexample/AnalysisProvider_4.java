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

package org.e2immu.analyser.parser.own.snippet.testexample;

import java.util.List;

// stripped the delay problem of AnalysisProvider_2
// cycle of three
public interface AnalysisProvider_4 {
    record TypeInfo(String name) {
    }

    enum Cause {
        TYPE_ANALYSIS, C1, C2, C3;
    }

    record DV(int value, List<Cause> causes) {
    }

    record ParameterizedType(TypeInfo bestTypeInfo, List<ParameterizedType> parameters) {
    }

    DV EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV = new DV(805, List.of(Cause.C1, Cause.C3));

    default DV a(ParameterizedType a0) {
        return b(a0, 1);
    }

    default DV b(ParameterizedType b0, int n) {
        DV paramValue = n < 10 ? c(b0) : EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV;
        return sumImmutableLevels(paramValue);
    }

    default DV c(ParameterizedType c0) {
        return a(c0);
    }

    DV sumImmutableLevels(DV paramValue);

}
