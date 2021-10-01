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

import java.io.PrintStream;
import java.io.Writer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestCommonJavaIO extends CommonAnnotatedAPI {

    @Test
    public void testPrintStream() {
        TypeInfo typeInfo = typeContext.getFullyQualified(PrintStream.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();

        assertEquals(MultiLevel.INDEPENDENT, typeAnalysis.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE, typeAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(Level.TRUE, typeAnalysis.getProperty(VariableProperty.CONTAINER));
    }

    @Test
    public void testPrintStreamPrint() {
        TypeInfo typeInfo = typeContext.getFullyQualified(PrintStream.class);
        TypeInfo charTypeInfo = typeContext.getPrimitives().charTypeInfo;
        MethodInfo methodInfo = typeInfo.findUniqueMethod("print", charTypeInfo);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));

        assertTrue(methodInfo.methodResolution.get().allowsInterrupts());
    }

    @Test
    public void testPrintStreamPrintln() {
        TypeInfo typeInfo = typeContext.getFullyQualified(PrintStream.class);
        TypeInfo objectTypeInfo = typeContext.getPrimitives().objectTypeInfo;
        MethodInfo methodInfo = typeInfo.findUniqueMethod("println", objectTypeInfo);

        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));

        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.NULLABLE, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        assertEquals(Level.FALSE, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));

        assertTrue(methodInfo.methodResolution.get().allowsInterrupts());
    }

    @Test
    public void testWriterAppendChar() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Writer.class);
        TypeInfo charTypeInfo = typeContext.getPrimitives().charTypeInfo;
        MethodInfo methodInfo = typeInfo.findUniqueMethod("append", charTypeInfo);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();

        assertEquals(MultiLevel.INDEPENDENT, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));

    }
}
