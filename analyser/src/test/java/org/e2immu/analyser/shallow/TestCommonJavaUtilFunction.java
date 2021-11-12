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

import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.junit.jupiter.api.Test;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCommonJavaUtilFunction extends CommonAnnotatedAPI {

    @Test
    public void testConsumerAccept() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Consumer.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("accept", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.TRUE_DV, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));

        // key
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(Level.TRUE_DV, p0.getProperty(VariableProperty.MODIFIED_VARIABLE), "in "+methodInfo.fullyQualifiedName);
    }

    @Test
    public void testFunctionApply() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Function.class);
        assertTrue(typeInfo.isInterface());
        assertTrue(typeInfo.typeInspection.get().isFunctionalInterface());

        MethodInfo methodInfo = typeInfo.findUniqueMethod("apply", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.TRUE_DV, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));

        // key
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(Level.TRUE_DV, p0.getProperty(VariableProperty.MODIFIED_VARIABLE), "in "+methodInfo.fullyQualifiedName);
    }
}
