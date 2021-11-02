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

import org.e2immu.analyser.analyser.FieldAnalysisImpl;
import org.e2immu.analyser.analyser.PropertyException;
import org.e2immu.analyser.analyser.TypeAnalysisImpl;
import org.e2immu.analyser.analyser.VariableProperty;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
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
                .addDebugLogTargets(Stream.of(DELAYED, ANALYSER)
                        .map(Enum::toString).collect(Collectors.joining(",")))
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

        long infoTypeAnalysisNotAvailable = messages.stream()
                .filter(m -> m.message() == Message.Label.TYPE_ANALYSIS_NOT_AVAILABLE)
                .count();
        LOGGER.info("Have {} info messages: type analysis not available", infoTypeAnalysisNotAvailable);

        long errors = messages.stream()
                .filter(m -> m.message().severity == Message.Severity.ERROR)
                .count();
        LOGGER.info("Have {} error messages", errors);

        long javaLangErrors = messages.stream()
                .filter(m -> m.message().severity == Message.Severity.ERROR)
                .filter(m -> !m.location().info.getTypeInfo().packageName().startsWith("java.lang"))
                .count();
        LOGGER.info("Have {} error messages outside java.lang.*", javaLangErrors);
        assertEquals(0L, javaLangErrors);
    }

    // hardcoded
    @Test
    public void testObjectEquals() {
        TypeInfo object = typeContext.getFullyQualified(Object.class);
        TypeAnalysis typeAnalysis = object.typeAnalysis.get();
        assertEquals(Level.TRUE, typeAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE, typeAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT, typeAnalysis.getProperty(VariableProperty.INDEPENDENT));

        MethodInfo equals = object.findUniqueMethod("equals", 1);
        MethodAnalysis methodAnalysis = equals.methodAnalysis.get();
        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
    }


    // not hardcoded
    @Test
    public void testOptionalEquals() {
        TypeInfo optional = typeContext.getFullyQualified(Optional.class);
        TypeAnalysis typeAnalysis = optional.typeAnalysis.get();
        assertEquals(Level.FALSE, typeAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(MultiLevel.MUTABLE, typeAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(MultiLevel.DEPENDENT, typeAnalysis.getProperty(VariableProperty.INDEPENDENT));

        MethodInfo equals = optional.findUniqueMethod("equals", 1);
        MethodAnalysis methodAnalysis = equals.methodAnalysis.get();
        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
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
        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE, sizeAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT, sizeAnalysis.getProperty(VariableProperty.INDEPENDENT));

        // METHOD 2

        MethodInfo addAll = collection.findUniqueMethod("addAll", 1);
        MethodAnalysis addAllAnalysis = addAll.methodAnalysis.get();
        assertEquals(Level.FALSE, addAllAnalysis.getProperty(VariableProperty.FLUENT));
        assertEquals(Level.FALSE, addAllAnalysis.getProperty(VariableProperty.IDENTITY));
        assertEquals(Level.FALSE, addAllAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));

        // PARAMETER 1

        ParameterAnalysis addAll0 = addAll.parameterAnalysis(0);

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
        assertEquals(MultiLevel.NOT_INVOLVED, getAnalysis.getProperty(VariableProperty.IMMUTABLE));

        // an unbound type parameter cannot be DEPENDENT
        assertEquals(MultiLevel.INDEPENDENT_1, getAnalysis.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(MultiLevel.NULLABLE, getAnalysis.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
    }

    @Test
    public void testListAdd() {
        TypeInfo list = typeContext.getFullyQualified(List.class);
        TypeAnalysis typeAnalysis = list.typeAnalysis.get();
        assertEquals(Level.FALSE, typeAnalysis.getProperty(VariableProperty.CONTAINER));

        MethodInfo add = list.findUniqueMethod("add", 1);
        MethodAnalysis addAnalysis = add.methodAnalysis.get();

        assertEquals(Level.TRUE, addAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(Level.FALSE, addAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE, addAnalysis.getProperty(VariableProperty.IMMUTABLE));

        ParameterAnalysis paramAnalysis = add.parameterAnalysis(0);

        assertEquals(MultiLevel.NOT_INVOLVED, paramAnalysis.getProperty(VariableProperty.IMMUTABLE), "In " + add.fullyQualifiedName);
        assertEquals(MultiLevel.NULLABLE, paramAnalysis.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        // not a container!
        assertEquals(Level.TRUE, paramAnalysis.getProperty(VariableProperty.MODIFIED_VARIABLE));

        // independent, because the method is @NotModified and returns a boolean
        assertEquals(MultiLevel.INDEPENDENT, paramAnalysis.getProperty(VariableProperty.INDEPENDENT));
    }

    @Test
    public void testArrayListAddIndex() {
        TypeInfo list = typeContext.getFullyQualified(ArrayList.class);
        TypeAnalysis typeAnalysis = list.typeAnalysis.get();

        MethodInfo add = list.findUniqueMethod("add", 2);
        MethodAnalysis addAnalysis = add.methodAnalysis.get();

        assertEquals(Level.FALSE, addAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));

        ParameterAnalysis paramAnalysis = add.parameterAnalysis(1);

        // independent, because the method is @NotModified
        assertEquals(MultiLevel.INDEPENDENT, paramAnalysis.getProperty(VariableProperty.INDEPENDENT));
    }

    // Collection.toArray(T[] array) returns T[] array
    @Test
    public void testArrayAsParameterAndReturnType() {
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);

        MethodInfo toArray = collection.typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> "toArray".equals(m.name))
                .filter(m -> m.methodInspection.get().getParameters().size() == 1 &&
                        m.methodInspection.get().getParameters().get(0).parameterizedType.arrays == 1)
                .findFirst().orElseThrow();
        ParameterAnalysis p0 = toArray.parameterAnalysis(0);

        assertEquals(Level.TRUE, p0.getProperty(VariableProperty.IDENTITY));
        assertEquals(Level.TRUE, p0.getProperty(VariableProperty.CONTAINER));
        assertEquals(MultiLevel.NULLABLE, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.EFFECTIVELY_E1IMMUTABLE, p0.getProperty(VariableProperty.IMMUTABLE));
        // parameters of abstract methods are @Modified by default (unless primitive, E2)
        assertEquals(Level.TRUE, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
        assertEquals(MultiLevel.INDEPENDENT, p0.getProperty(VariableProperty.INDEPENDENT));

        MethodAnalysis toArrayAnalysis = toArray.methodAnalysis.get();
        assertEquals(Level.TRUE, toArrayAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(Level.FALSE, toArrayAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));

        assertEquals(MultiLevel.NULLABLE, toArrayAnalysis.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        assertEquals(MultiLevel.EFFECTIVELY_E1IMMUTABLE, toArrayAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT_1, toArrayAnalysis.getProperty(VariableProperty.INDEPENDENT));
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

        ParameterAnalysis intAnalysis = twoConstructor.parameterAnalysis(0);

        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL, intAnalysis.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE, intAnalysis.getProperty(VariableProperty.IMMUTABLE));
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
            assertTrue(outAnalysisImpl.properties.containsKey(VariableProperty.FINAL));
            assertTrue(outAnalysisImpl.properties.containsKey(VariableProperty.CONTAINER));
        }
    }

    @Test
    public void testIntField() {
        TypeInfo integer = typeContext.getFullyQualified(Integer.class);
        FieldInfo bytes = integer.getFieldByName("BYTES", true);
        FieldAnalysis bytesAnalysis = bytes.fieldAnalysis.get();

        assertEquals(Level.TRUE, bytesAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE, bytesAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(Level.TRUE, bytesAnalysis.getProperty(VariableProperty.FINAL));
        assertEquals(Level.FALSE, bytesAnalysis.getProperty(VariableProperty.MODIFIED_OUTSIDE_METHOD));
        assertEquals(MultiLevel.INDEPENDENT, bytesAnalysis.getProperty(VariableProperty.INDEPENDENT));

        if (bytesAnalysis instanceof FieldAnalysisImpl bytesAnalysisImpl) {
            assertTrue(bytesAnalysisImpl.properties.containsKey(VariableProperty.CONTAINER));
            assertTrue(bytesAnalysisImpl.properties.containsKey(VariableProperty.FINAL));
        } else fail();

    }

    /**
     * By default, {@link Throwable} is @Dependent
     */
    @Test
    public void testThrowable() {
        TypeInfo throwable = typeContext.getFullyQualified(Throwable.class);
        TypeAnalysis typeAnalysis = throwable.typeAnalysis.get();
        assertEquals(Level.FALSE, typeAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(MultiLevel.MUTABLE, typeAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(MultiLevel.DEPENDENT, typeAnalysis.getProperty(VariableProperty.INDEPENDENT));

        MethodInfo equals = throwable.findUniqueMethod("getStackTrace", 0);
        MethodAnalysis methodAnalysis = equals.methodAnalysis.get();
        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
    }


    /**
     * {@link IllegalFormatException} has no methods or constructors of its own, but it does share those of
     * {@link Throwable} and {@link Object}. As a consequence, it is {@link org.e2immu.annotation.Dependent}.
     */
    @Test
    public void testIllegalFormatException() {
        TypeInfo illegalFormatException = typeContext.getFullyQualified(IllegalFormatException.class);
        TypeAnalysis typeAnalysis = illegalFormatException.typeAnalysis.get();
        assertEquals(Level.FALSE, typeAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(MultiLevel.MUTABLE, typeAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(MultiLevel.DEPENDENT, typeAnalysis.getProperty(VariableProperty.INDEPENDENT));
    }


    @Test
    public void testMapPut() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Map.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        assertEquals(Level.FALSE, typeAnalysis.getProperty(VariableProperty.CONTAINER));

        MethodInfo methodInfo = typeInfo.findUniqueMethod("put", 2);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_1, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));

        // key
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.NULLABLE, p0.getProperty(VariableProperty.NOT_NULL_PARAMETER));
        assertEquals(Level.TRUE, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
        assertEquals(MultiLevel.INDEPENDENT, p0.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(MultiLevel.NOT_INVOLVED, p0.getProperty(VariableProperty.IMMUTABLE));
    }


    @Test
    public void testRandomNextInt() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Random.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("nextInt", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
    }


    @Test
    public void testObjectsRequireNonNull() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Objects.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("requireNonNull", 2);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
        assertEquals(Level.FALSE, methodAnalysis.getProperty(VariableProperty.IDENTITY));
        ParameterAnalysis p0 = methodAnalysis.getParameterAnalyses().get(0);
        assertEquals(Level.TRUE, p0.getProperty(VariableProperty.MODIFIED_VARIABLE));
    }
}
