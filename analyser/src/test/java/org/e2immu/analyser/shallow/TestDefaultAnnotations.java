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

import org.e2immu.analyser.analyser.*;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.junit.jupiter.api.Assertions.*;

/*
Tests the default annotations of byte-code inspected+analysed types, without annotated APIs on top.

All property maps are empty at this stage!

 */
public class TestDefaultAnnotations {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestDefaultAnnotations.class);

    private static TypeContext typeContext;

    @BeforeAll
    public static void beforeClass() throws IOException {
        InputConfiguration.Builder inputConfigurationBuilder = new InputConfiguration.Builder()
                .addClassPath("jmods/java.base.jmod");
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(inputConfigurationBuilder.build())
                .addDebugLogTargets(Stream.of(DELAYED).map(Enum::toString).collect(Collectors.joining(",")))
                .build();
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        parser.preload("java.io"); // to compute properties on System.out; java.io.PrintStream
        parser.preload("java.util");
        parser.preload("java.util.stream");
        parser.run();
        typeContext = parser.getTypeContext();
        List<Message> messages = parser.getMessages().toList();
        for (Message message : messages) {
            LOGGER.info("Message: {}", message);
        }
        LOGGER.info("Have {} messages", messages.size());

        long ownErrors = messages.stream()
                .filter(m -> m.location().info.getTypeInfo().fullyQualifiedName.startsWith("org.e2immu"))
                .peek(m -> LOGGER.info("OWN ERROR: {}", m))
                .count();
        assertEquals(0L, ownErrors);
    }

    @Test
    public void testCollection() {
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        assertNotNull(collection);

        TypeAnalysis typeAnalysis = collection.typeAnalysis.get();
        assertEquals(Level.FALSE, typeAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(MultiLevel.MUTABLE, typeAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(MultiLevel.DEPENDENT, typeAnalysis.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(Level.FALSE, typeAnalysis.getProperty(VariableProperty.EXTENSION_CLASS));
        assertEquals(Level.FALSE, typeAnalysis.getProperty(VariableProperty.UTILITY_CLASS));
        assertEquals(Level.FALSE, typeAnalysis.getProperty(VariableProperty.SINGLETON));
        assertEquals(Level.FALSE, typeAnalysis.getProperty(VariableProperty.FINALIZER));
        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(VariableProperty.FLUENT));
        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(VariableProperty.IDENTITY));
        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(VariableProperty.NOT_NULL_PARAMETER));

        if (typeAnalysis instanceof TypeAnalysisImpl typeAnalysisImpl) {
            assertTrue(typeAnalysisImpl.properties.isEmpty());
        } else fail();

        // METHOD 1

        MethodInfo size = collection.findUniqueMethod("size", 0);
        MethodAnalysis sizeAnalysis = size.methodAnalysis.get();
        assertEquals(Level.FALSE, sizeAnalysis.getProperty(VariableProperty.FLUENT));
        assertEquals(Level.FALSE, sizeAnalysis.getProperty(VariableProperty.IDENTITY));
        assertEquals(Level.FALSE, sizeAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertThrows(PropertyException.class, () -> sizeAnalysis.getProperty(VariableProperty.IGNORE_MODIFICATIONS));

        // properties of a primitive return type:
        assertEquals(Level.TRUE, sizeAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, sizeAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT, sizeAnalysis.getProperty(VariableProperty.INDEPENDENT));

        if (sizeAnalysis instanceof MethodAnalysisImpl sizeAnalysisImpl) {
            assertEquals(3, sizeAnalysisImpl.properties.size());
            assertTrue(sizeAnalysisImpl.properties.containsKey(VariableProperty.INDEPENDENT));
            assertTrue(sizeAnalysisImpl.properties.containsKey(VariableProperty.IMMUTABLE));
            assertTrue(sizeAnalysisImpl.properties.containsKey(VariableProperty.MODIFIED_METHOD));
        } else fail();

        // METHOD 2

        MethodInfo addAll = collection.findUniqueMethod("addAll", 1);
        MethodAnalysis addAllAnalysis = addAll.methodAnalysis.get();
        assertEquals(Level.FALSE, addAllAnalysis.getProperty(VariableProperty.FLUENT));
        assertEquals(Level.FALSE, addAllAnalysis.getProperty(VariableProperty.IDENTITY));
        assertEquals(Level.FALSE, addAllAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));

        // PARAMETER 1

        ParameterAnalysis addAll0 = addAll.methodInspection.get().getParameters().get(0).parameterAnalysis.get();

        assertEquals(Level.TRUE, addAll0.getProperty(VariableProperty.IDENTITY));
        assertEquals(Level.FALSE, addAll0.getProperty(VariableProperty.CONTAINER));
        assertEquals(Level.FALSE, addAll0.getProperty(VariableProperty.IGNORE_MODIFICATIONS));
        // method is non-modifying, so parameter is independent
        assertEquals(MultiLevel.INDEPENDENT, addAll0.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE, addAll0.getProperty(VariableProperty.IMMUTABLE));

        assertThrows(PropertyException.class, () -> addAll0.getProperty(VariableProperty.MODIFIED_METHOD));
        assertThrows(PropertyException.class, () -> addAll0.getProperty(VariableProperty.EXTENSION_CLASS));
        assertThrows(PropertyException.class, () -> addAll0.getProperty(VariableProperty.SINGLETON));
        assertThrows(PropertyException.class, () -> addAll0.getProperty(VariableProperty.FLUENT));
        assertThrows(PropertyException.class, () -> addAll0.getProperty(VariableProperty.FINALIZER));
        assertThrows(PropertyException.class, () -> addAll0.getProperty(VariableProperty.FINAL));
    }

    @Test
    public void testListGet() {
        TypeInfo list = typeContext.getFullyQualified(List.class);
        MethodInfo get = list.findUniqueMethod("get", 1);
        MethodAnalysis getAnalysis = get.methodAnalysis.get();

        assertEquals(Level.TRUE, getAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(Level.FALSE, getAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, getAnalysis.getProperty(VariableProperty.IMMUTABLE));

        // an unbound type parameter cannot be DEPENDENT
        assertEquals(MultiLevel.DEPENDENT_1, getAnalysis.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(MultiLevel.NULLABLE, getAnalysis.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
    }

    @Test
    public void testListAdd() {
        TypeInfo list = typeContext.getFullyQualified(List.class);
        MethodInfo add = list.findUniqueMethod("add", 1);
        MethodAnalysis addAnalysis = add.methodAnalysis.get();

        assertEquals(Level.TRUE, addAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(Level.FALSE, addAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, addAnalysis.getProperty(VariableProperty.IMMUTABLE));

        ParameterAnalysis paramAnalysis = add.methodInspection.get().getParameters().get(0).parameterAnalysis.get();

        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, paramAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(MultiLevel.NULLABLE, paramAnalysis.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        assertEquals(Level.FALSE, addAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE));

        // independent, because the method is @NotModified and returns a boolean
        assertEquals(MultiLevel.INDEPENDENT, paramAnalysis.getProperty(VariableProperty.INDEPENDENT));
    }

    // Collection.toArray(T[] array) returns array
    @Test
    public void testArrayAsParameterAndReturnType() {
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);

        MethodInfo toArray = collection.typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> "toArray".equals(m.name))
                .filter(m -> m.methodInspection.get().getParameters().size() == 1 &&
                        m.methodInspection.get().getParameters().get(0).parameterizedType.arrays == 1)
                .findFirst().orElseThrow();
        ParameterInfo tArray = toArray.methodInspection.get().getParameters().get(0);
        ParameterAnalysis tArrayAnalysis = tArray.parameterAnalysis.get();

        assertEquals(Level.TRUE, tArrayAnalysis.getProperty(VariableProperty.IDENTITY));
        assertEquals(Level.TRUE, tArrayAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(MultiLevel.NULLABLE, tArrayAnalysis.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.EFFECTIVELY_E1IMMUTABLE, tArrayAnalysis.getProperty(VariableProperty.IMMUTABLE));
        // parameters of abstract methods are @Modified by default (unless primitive, E2)
        assertEquals(Level.TRUE, tArrayAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE));

        MethodAnalysis toArrayAnalysis = toArray.methodAnalysis.get();
        assertEquals(Level.TRUE, toArrayAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(MultiLevel.NULLABLE, toArrayAnalysis.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        assertEquals(MultiLevel.EFFECTIVELY_E1IMMUTABLE, toArrayAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(MultiLevel.DEPENDENT, toArrayAnalysis.getProperty(VariableProperty.INDEPENDENT));

    }

    @Test
    public void testConstructor() {

        // CONSTRUCTOR 1

        TypeInfo arrayList = typeContext.getFullyQualified(ArrayList.class);

        MethodInfo emptyConstructor = arrayList.findConstructor(0);
        MethodAnalysis emptyAnalysis = emptyConstructor.methodAnalysis.get();
        assertEquals(MultiLevel.INDEPENDENT, emptyAnalysis.getProperty(VariableProperty.INDEPENDENT));

        // all constructors are modified, by definition
        assertEquals(Level.TRUE, emptyAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));

        TypeInfo hashMap = typeContext.getFullyQualified(HashMap.class);

        // CONSTRUCTOR 2

        // constructor with int and float parameters
        MethodInfo twoConstructor = hashMap.findConstructor(2);
        MethodAnalysis twoAnalysis = twoConstructor.methodAnalysis.get();
        assertEquals(MultiLevel.INDEPENDENT, twoAnalysis.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(Level.TRUE, twoAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));

        ParameterInfo intParam = twoConstructor.methodInspection.get().getParameters().get(0);
        ParameterAnalysis intAnalysis = intParam.parameterAnalysis.get();

        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, intAnalysis.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, intAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(Level.TRUE, intAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(Level.FALSE, intAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE));
        assertThrows(PropertyException.class, () -> intAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
    }

    @Test
    public void testField() {
        TypeInfo system = typeContext.getFullyQualified(System.class);
        FieldInfo out = system.getFieldByName("out", true);
        FieldAnalysis outAnalysis = out.fieldAnalysis.get();

        assertEquals(Level.FALSE, outAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(MultiLevel.MUTABLE, outAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(Level.TRUE, outAnalysis.getProperty(VariableProperty.FINAL));
        assertEquals(Level.FALSE, outAnalysis.getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
        assertEquals(MultiLevel.DEPENDENT, outAnalysis.getProperty(VariableProperty.INDEPENDENT));

        if (outAnalysis instanceof FieldAnalysisImpl outAnalysisImpl) {
            assertEquals(1, outAnalysisImpl.properties.size());
            assertTrue(outAnalysisImpl.properties.containsKey(VariableProperty.FINAL));
        } else fail();
    }

    @Test
    public void testIntField() {
        TypeInfo integer = typeContext.getFullyQualified(Integer.class);
        FieldInfo bytes = integer.getFieldByName("BYTES", true);
        FieldAnalysis bytesAnalysis = bytes.fieldAnalysis.get();

        assertEquals(Level.TRUE, bytesAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, bytesAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(Level.TRUE, bytesAnalysis.getProperty(VariableProperty.FINAL));
        assertEquals(Level.FALSE, bytesAnalysis.getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
        assertEquals(MultiLevel.INDEPENDENT, bytesAnalysis.getProperty(VariableProperty.INDEPENDENT));

        if (bytesAnalysis instanceof FieldAnalysisImpl bytesAnalysisImpl) {
            assertEquals(1, bytesAnalysisImpl.properties.size());
            assertTrue(bytesAnalysisImpl.properties.containsKey(VariableProperty.FINAL));
        } else fail();

    }
}
