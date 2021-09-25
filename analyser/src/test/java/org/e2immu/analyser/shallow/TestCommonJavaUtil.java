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

import org.e2immu.analyser.analyser.PropertyException;
import org.e2immu.analyser.analyser.VariableProperty;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Primitives;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

public class TestCommonJavaUtil extends CommonAnnotatedAPI {


    @Test
    public void testCollection() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();

        assertEquals(MultiLevel.DEPENDENT_1, typeAnalysis.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE, typeAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(Level.TRUE, typeAnalysis.getProperty(VariableProperty.CONTAINER));
    }

    @Test
    public void testCollectionAdd() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("add", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.TRUE,  methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_1, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
    }

    @Test
    public void testCollectionAddAll() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("addAll", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.TRUE,  methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_1, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));

        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.DEPENDENT_1, p0.getProperty(VariableProperty.INDEPENDENT));
    }

    @Test
    public void testCollectionForEach() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("forEach", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.FALSE,  methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_1, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));

        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.DEPENDENT_1, p0.getProperty(VariableProperty.INDEPENDENT));
    }

    @Test
    public void testAbstractCollectionAddAll() {
        TypeInfo typeInfo = typeContext.getFullyQualified(AbstractCollection.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("addAll", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.TRUE,  methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_1, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));

        // this statement catches the same problem as the DEP_1 on p0
        assertFalse( errors.stream().anyMatch(m -> m.location().info.getTypeInfo().equals(typeInfo)));

        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.DEPENDENT_1, p0.getProperty(VariableProperty.INDEPENDENT));
    }

    @Test
    public void testListAdd() {
        TypeInfo typeInfo = typeContext.getFullyQualified(ArrayList.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("add", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.TRUE,  methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_1, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
    }

    @Test
    public void testArrayListAdd() {
        TypeInfo typeInfo = typeContext.getFullyQualified(ArrayList.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("add", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.TRUE,  methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_1, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
    }

}
