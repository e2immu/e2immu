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

package org.e2immu.analyser.shallow;

import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.junit.jupiter.api.Test;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCommonJavaUtilStream extends CommonAnnotatedAPI {

    @Test
    public void testStream() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Stream.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        assertEquals(MultiLevel.INDEPENDENT_1_DV, typeAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(Level.TRUE_DV, typeAnalysis.immutableCanBeIncreasedByTypeParameters());
    }

    @Test
    public void testStreamMap() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Stream.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("map", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_1_DV, methodAnalysis.getProperty(Property.INDEPENDENT));

        // key
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(Level.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
        assertEquals(MultiLevel.INDEPENDENT_1_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE_DV, p0.getProperty(Property.IMMUTABLE));
    }


    /*
    static Stream<T> of(T)

    T is minimally @Independent1, as an unbound type parameter.
    Stream<T> is minimally @Independent1, as it is formally @E2Container.
    The parameter should be @Independent if there is no content link between the parameter and the method result.
     */
    @Test
    public void testStreamOf() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Stream.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("of", 1);
        assertTrue(methodInfo.methodInspection.get().isStatic());

        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_1_DV, methodAnalysis.getProperty(Property.INDEPENDENT),
                methodInfo.fullyQualifiedName);

        // T
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(Level.FALSE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
        assertEquals(MultiLevel.INDEPENDENT_1_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.NOT_INVOLVED_DV, p0.getProperty(Property.IMMUTABLE));
    }
}
