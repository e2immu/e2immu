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
        assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_1, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));

        // as opposed to java.io.PrintStream.print(X x), for example
        assertFalse(methodInfo.methodResolution.get().allowsInterrupts());
    }

    @Test
    public void testCollectionAddAll() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("addAll", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
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
        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_1, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));

        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.DEPENDENT_1, p0.getProperty(VariableProperty.INDEPENDENT));
    }

    @Test
    public void testAbstractCollection() {
        TypeInfo typeInfo = typeContext.getFullyQualified(AbstractCollection.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        // not explicitly marked; no inheritance (we can go down from @Dependent1 to @Dependent)
        assertEquals(MultiLevel.DEPENDENT, typeAnalysis.getProperty(VariableProperty.INDEPENDENT));

        assertFalse(errors.stream().anyMatch(m -> m.location().info.getTypeInfo().equals(typeInfo)),
                "Got: " + errors.stream().filter(m -> m.location().info.getTypeInfo().equals(typeInfo)).toList());
    }

    @Test
    public void testAbstractCollectionAddAll() {
        TypeInfo typeInfo = typeContext.getFullyQualified(AbstractCollection.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("addAll", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_1, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));

        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.DEPENDENT_1, p0.getProperty(VariableProperty.INDEPENDENT));
    }

    @Test
    public void testAbstractCollectionToArray() {
        TypeInfo typeInfo = typeContext.getFullyQualified(AbstractCollection.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("toArray", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_E1IMMUTABLE, methodAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(MultiLevel.DEPENDENT_1, methodAnalysis.getProperty(VariableProperty.INDEPENDENT),
                "Method "+methodInfo.fullyQualifiedName);
        // the value should be the one in the map; for speed reasons, we should not be looking at overrides!
        int inMap = ((MethodAnalysisImpl) methodAnalysis).properties.get(VariableProperty.INDEPENDENT);
        assertEquals(MultiLevel.DEPENDENT_1, inMap);
    }

    @Test
    public void testCollectionToArrayIntFunction() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        TypeInfo intFunction = typeContext.getFullyQualified(IntFunction.class);
        assertNotNull(intFunction);

        MethodInfo methodInfo = typeInfo.findUniqueMethod("toArray", intFunction);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_E1IMMUTABLE, methodAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(MultiLevel.DEPENDENT_1, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
    }

    @Test
    public void testListAdd() {
        TypeInfo typeInfo = typeContext.getFullyQualified(ArrayList.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("add", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_1, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
    }

    @Test
    public void testArrayListAdd() {
        TypeInfo typeInfo = typeContext.getFullyQualified(ArrayList.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("add", 1);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_1, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
    }

    @Test
    public void testCollectionStream() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collection.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("stream", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_CONTENT_NOT_NULL, methodAnalysis.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        assertEquals(MultiLevel.DEPENDENT_1, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, methodAnalysis.getProperty(VariableProperty.IMMUTABLE));
    }

    @Test
    public void testCollections() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Collections.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();

        assertEquals(Level.TRUE, typeAnalysis.getProperty(VariableProperty.UTILITY_CLASS));
        assertEquals(Level.FALSE, typeAnalysis.getProperty(VariableProperty.CONTAINER)); // Collections.addAll
        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, typeAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT, typeAnalysis.getProperty(VariableProperty.INDEPENDENT)); // no data
    }


    @Test
    public void testMapPut() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Map.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("put", 2);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_1, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));

        // key
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        assertEquals(Level.FALSE, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
        assertEquals(MultiLevel.DEPENDENT_1, p0.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, p0.getProperty(VariableProperty.IMMUTABLE));
    }
}
