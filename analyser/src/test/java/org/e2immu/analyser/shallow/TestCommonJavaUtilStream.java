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

import org.e2immu.analyser.analyser.MethodAnalysisImpl;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.function.IntFunction;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TestCommonJavaUtilStream extends CommonAnnotatedAPI {

    @Test
    public void testStreamMap() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Stream.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("map", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_1, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));

        // key
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        assertEquals(Level.FALSE, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
        assertEquals(MultiLevel.DEPENDENT_1, p0.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE, p0.getProperty(VariableProperty.IMMUTABLE));
    }
}
