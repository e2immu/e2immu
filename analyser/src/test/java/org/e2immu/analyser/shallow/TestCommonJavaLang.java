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

import java.io.Serializable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestCommonJavaLang extends CommonAnnotatedAPI {

    /* @Independent set by hand, because the class structure is pretty complex, and we have not annotated
      all these types yet
     */
    @Test
    public void testClass() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Class.class);
        TypeAnalysis objectAnalysis = typeInfo.typeAnalysis.get();
        testE2ContainerType(objectAnalysis);
        assertEquals(MultiLevel.INDEPENDENT, objectAnalysis.getProperty(VariableProperty.INDEPENDENT));

        MethodInfo methodInfo = typeInfo.findUniqueMethod("getAnnotatedInterfaces",0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();

        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.FLUENT));
        assertEquals(MultiLevel.INDEPENDENT, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_E1IMMUTABLE, methodAnalysis.getProperty(VariableProperty.IMMUTABLE));
    }

    @Test
    public void testObject() {
        TypeInfo object = typeContext.getFullyQualified(Object.class);
        TypeAnalysis objectAnalysis = object.typeAnalysis.get();
        testE2ContainerType(objectAnalysis);
        assertEquals(MultiLevel.INDEPENDENT, objectAnalysis.getProperty(VariableProperty.INDEPENDENT));
    }

    @Test
    public void testString() {
        TypeInfo string = typeContext.getFullyQualified(String.class);
        TypeAnalysis typeAnalysis = string.typeAnalysis.get();
        testE2ContainerType(typeAnalysis);
        assertEquals(MultiLevel.INDEPENDENT, typeAnalysis.getProperty(VariableProperty.INDEPENDENT));
    }


    @Test
    public void testObjectToString() {
        TypeInfo object = typeContext.getFullyQualified(Object.class);
        MethodInfo toString = object.findUniqueMethod("toString", 0);
        MethodAnalysis methodAnalysis = toString.methodAnalysis.get();

        assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, methodAnalysis.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));

        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, methodAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
    }

    @Test
    public void testStringBuilder() {
        TypeInfo sb = typeContext.getFullyQualified(StringBuilder.class);
        TypeAnalysis typeAnalysis = sb.typeAnalysis.get();

        {
            MethodInfo appendBoolean = sb.typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                    .filter(m -> "append".equals(m.name))
                    .filter(m -> m.methodInspection.get().getParameters().size() == 1 &&
                            Primitives.isBoolean(m.methodInspection.get().getParameters().get(0).parameterizedType))
                    .findFirst().orElseThrow();
            MethodAnalysis methodAnalysis = appendBoolean.methodAnalysis.get();

            // a @Fluent method in a modifiable type is @Dependent (it returns 'this', which is modifiable)
            assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.FLUENT));
            assertEquals(MultiLevel.DEPENDENT, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
            assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
            assertEquals(MultiLevel.MUTABLE, methodAnalysis.getProperty(VariableProperty.IMMUTABLE));

            ParameterAnalysis p0 = appendBoolean.methodInspection.get().getParameters().get(0).parameterAnalysis.get();
            assertEquals(MultiLevel.INDEPENDENT, p0.getProperty(VariableProperty.INDEPENDENT));
            assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, p0.getProperty(VariableProperty.IMMUTABLE));
            assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
            assertEquals(Level.FALSE, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
            assertThrows(PropertyException.class, () -> p0.getProperty(VariableProperty.MODIFIED_METHOD));
        }
        {
            MethodInfo appendString = sb.typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                    .filter(m -> "append".equals(m.name))
                    .filter(m -> m.methodInspection.get().getParameters().size() == 1 &&
                            Primitives.isJavaLangString(m.methodInspection.get().getParameters().get(0).parameterizedType))
                    .findFirst().orElseThrow();
            MethodAnalysis methodAnalysis = appendString.methodAnalysis.get();

            // a @Fluent method in a modifiable type is @Dependent (it returns 'this', which is modifiable)
            assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.FLUENT));
            assertEquals(MultiLevel.DEPENDENT, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
            assertEquals(Level.TRUE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
            assertEquals(MultiLevel.MUTABLE, methodAnalysis.getProperty(VariableProperty.IMMUTABLE));

            ParameterAnalysis p0 = appendString.methodInspection.get().getParameters().get(0).parameterAnalysis.get();
            assertEquals(MultiLevel.INDEPENDENT, p0.getProperty(VariableProperty.INDEPENDENT));
            assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, p0.getProperty(VariableProperty.IMMUTABLE));
            assertEquals(MultiLevel.NULLABLE, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
            assertEquals(Level.FALSE, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
        }

        // as a consequence, the type is DEPENDENT as well
        assertEquals(MultiLevel.DEPENDENT, typeAnalysis.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(Level.TRUE, typeAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(MultiLevel.MUTABLE, typeAnalysis.getProperty(VariableProperty.IMMUTABLE));
    }

    @Test
    public void testCharSequence() {
        TypeInfo string = typeContext.getFullyQualified(String.class);
        TypeAnalysis typeAnalysis = string.typeAnalysis.get();
        testE2ContainerType(typeAnalysis);
        assertEquals(MultiLevel.INDEPENDENT, typeAnalysis.getProperty(VariableProperty.INDEPENDENT));
    }

    @Test
    public void testComparable() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Comparable.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        testE2ContainerType(typeAnalysis);
        assertEquals(MultiLevel.INDEPENDENT, typeAnalysis.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, typeAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(Level.TRUE, typeAnalysis.getProperty(VariableProperty.CONTAINER));

        MethodInfo compareTo = typeInfo.findUniqueMethod("compareTo",1);
        MethodAnalysis methodAnalysis = compareTo.methodAnalysis.get();

        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.FLUENT));
        assertEquals(MultiLevel.INDEPENDENT, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, methodAnalysis.getProperty(VariableProperty.IMMUTABLE));

        ParameterAnalysis p0 = compareTo.methodInspection.get().getParameters().get(0).parameterAnalysis.get();
        assertEquals(MultiLevel.INDEPENDENT, p0.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, p0.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        assertEquals(Level.FALSE, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
    }

    @Test
    public void testSerializable() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Serializable.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        testE2ContainerType(typeAnalysis);
        assertEquals(MultiLevel.INDEPENDENT, typeAnalysis.getProperty(VariableProperty.INDEPENDENT));
    }

    @Test
    public void testAppendable() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Appendable.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        assertEquals(MultiLevel.DEPENDENT, typeAnalysis.getProperty(VariableProperty.INDEPENDENT));
    }
}
