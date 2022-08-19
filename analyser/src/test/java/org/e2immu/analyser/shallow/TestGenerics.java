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

import org.e2immu.analyser.analyser.AnalysisProvider;
import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.model.*;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestGenerics extends CommonAnnotatedAPI {

    @Test
    public void testStreamInteger() {
        TypeInfo stream = typeContext.getFullyQualified(Stream.class);
        TypeAnalysis streamAnalysis = stream.typeAnalysis.get();
        assertEquals(MultiLevel.INDEPENDENT_1_DV, streamAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, streamAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(DV.TRUE_DV, streamAnalysis.immutableDeterminedByTypeParameters());

        TypeInfo integer = typeContext.getFullyQualified(Integer.class);
        TypeAnalysis integerAnalysis = integer.typeAnalysis.get();
        assertEquals(MultiLevel.INDEPENDENT_DV, integerAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, integerAnalysis.getProperty(Property.IMMUTABLE));

        ParameterizedType integerPt = new ParameterizedType(integer, 0);

        ParameterizedType streamOfIntegers = new ParameterizedType(stream, List.of(integerPt));
        assertEquals("Type java.util.stream.Stream<java.lang.Integer>", streamOfIntegers.toString());

        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, AnalysisProvider.DEFAULT_PROVIDER
                .defaultImmutable(streamOfIntegers, false, null));
    }

    @Test
    public void testStreamOptionalInteger() {
        TypeInfo stream = typeContext.getFullyQualified(Stream.class);
        TypeInfo integer = typeContext.getFullyQualified(Integer.class);

        TypeInfo optional = typeContext.getFullyQualified(Optional.class);
        TypeAnalysis optionalAnalysis = optional.typeAnalysis.get();
        assertEquals(MultiLevel.INDEPENDENT_1_DV, optionalAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, optionalAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(DV.TRUE_DV, optionalAnalysis.immutableDeterminedByTypeParameters());

        ParameterizedType integerPt = new ParameterizedType(integer, 0);
        ParameterizedType optionalIntegerPt = new ParameterizedType(optional, List.of(integerPt));

        ParameterizedType streamOfOptionalIntegers = new ParameterizedType(stream, List.of(optionalIntegerPt));
        assertEquals("Type java.util.stream.Stream<java.util.Optional<java.lang.Integer>>",
                streamOfOptionalIntegers.toString());

        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV,
                AnalysisProvider.DEFAULT_PROVIDER.defaultImmutable(streamOfOptionalIntegers, false, null));
        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV,
                AnalysisProvider.DEFAULT_PROVIDER.defaultImmutable(streamOfOptionalIntegers, true, null));
    }

    /*
     Stream is E2Immutable.
     Entry is E2Immutable when the result from floorEntry in TreeMap, but not when returned as Set<Entry<...>> in Map.entrySet.
     Map.entrySet() is Set<Entry<>> is @Dependent/@Container, therefore increasing doesn't work
     */

    @Test
    public void testStreamEntryStringInteger() {
        TypeInfo stream = typeContext.getFullyQualified(Stream.class);
        TypeInfo integer = typeContext.getFullyQualified(Integer.class);
        TypeInfo string = typeContext.getFullyQualified(String.class);

        TypeInfo entry = typeContext.getFullyQualified(Map.Entry.class);
        TypeAnalysis entryAnalysis = entry.typeAnalysis.get();
        assertEquals(MultiLevel.INDEPENDENT_1_DV, entryAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE_DV, entryAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(DV.TRUE_DV, entryAnalysis.immutableDeterminedByTypeParameters());

        ParameterizedType integerPt = new ParameterizedType(integer, 0);
        ParameterizedType stringPt = new ParameterizedType(string, 0);
        ParameterizedType entryStringIntegerPt = new ParameterizedType(entry, List.of(stringPt, integerPt));

        ParameterizedType streamOfEntryStringIntegers = new ParameterizedType(stream, List.of(entryStringIntegerPt));
        assertEquals("Type java.util.stream.Stream<java.util.Map.Entry<java.lang.String,java.lang.Integer>>",
                streamOfEntryStringIntegers.toString());

        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV,
                AnalysisProvider.DEFAULT_PROVIDER.defaultImmutable(streamOfEntryStringIntegers, false, null));
    }

}
