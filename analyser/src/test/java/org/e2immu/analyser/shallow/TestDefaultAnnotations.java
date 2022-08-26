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

import org.e2immu.analyser.analyser.DV;
import org.e2immu.analyser.analyser.Property;
import org.e2immu.analyser.analyser.PropertyException;
import org.e2immu.analyser.analysis.FieldAnalysis;
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.ParameterAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.analysis.impl.FieldAnalysisImpl;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;

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
                .addDebugLogTargets("analyser")
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
                .filter(m -> !((LocationImpl) m.location()).info.getTypeInfo().packageName().startsWith("java.lang"))
                .count();
        LOGGER.info("Have {} error messages outside java.lang.*", javaLangErrors);
        assertEquals(0L, javaLangErrors);
    }

    // hardcoded
    @Test
    public void testObject() {
        TypeInfo object = typeContext.getFullyQualified(Object.class);
        TypeAnalysis typeAnalysis = object.typeAnalysis.get();
        assertEquals(MultiLevel.CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT_DV, typeAnalysis.getProperty(Property.INDEPENDENT));
    }

    @Test
    public void testObjectEquals() {
        TypeInfo object = typeContext.getFullyQualified(Object.class);
        MethodInfo equals = object.findUniqueMethod("equals", 1);
        MethodAnalysis methodAnalysis = equals.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
    }

    // not hardcoded
    @Test
    public void testOptional() {
        TypeInfo optional = typeContext.getFullyQualified(Optional.class);
        TypeAnalysis typeAnalysis = optional.typeAnalysis.get();
        assertEquals(MultiLevel.NOT_CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));
        assertEquals(MultiLevel.MUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.DEPENDENT_DV, typeAnalysis.getProperty(Property.INDEPENDENT));
    }

    // not hardcoded
    @Test
    public void testOptionalEquals() {
        TypeInfo optional = typeContext.getFullyQualified(Optional.class);
        MethodInfo equals = optional.findUniqueMethod("equals", 1);
        MethodAnalysis methodAnalysis = equals.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
    }

    @Test
    public void testCollection() {
        TypeInfo collection = typeContext.getFullyQualified(Collection.class);
        assertNotNull(collection);

        TypeAnalysis typeAnalysis = collection.typeAnalysis.get();
        assertEquals(MultiLevel.NOT_CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));
        assertEquals(MultiLevel.MUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.DEPENDENT_DV, typeAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(DV.FALSE_DV, typeAnalysis.getProperty(Property.EXTENSION_CLASS));
        assertEquals(DV.FALSE_DV, typeAnalysis.getProperty(Property.UTILITY_CLASS));
        assertEquals(DV.FALSE_DV, typeAnalysis.getProperty(Property.SINGLETON));
        assertEquals(DV.FALSE_DV, typeAnalysis.getProperty(Property.FINALIZER));
        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(Property.FLUENT));
        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(Property.IDENTITY));
        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(Property.NOT_NULL_EXPRESSION));
        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(Property.NOT_NULL_PARAMETER));

        // METHOD 1

        MethodInfo size = collection.findUniqueMethod("size", 0);
        MethodAnalysis sizeAnalysis = size.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, sizeAnalysis.getProperty(Property.FLUENT));
        assertEquals(DV.FALSE_DV, sizeAnalysis.getProperty(Property.IDENTITY));
        assertEquals(DV.FALSE_DV, sizeAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.NOT_IGNORE_MODS_DV, sizeAnalysis.getProperty(Property.IGNORE_MODIFICATIONS));

        // properties of a primitive return type:
        assertEquals(MultiLevel.CONTAINER_DV, sizeAnalysis.getProperty(Property.CONTAINER));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_DV, sizeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT_DV, sizeAnalysis.getProperty(Property.INDEPENDENT));

        // METHOD 2

        MethodInfo addAll = collection.findUniqueMethod("addAll", 1);
        MethodAnalysis addAllAnalysis = addAll.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, addAllAnalysis.getProperty(Property.FLUENT));
        assertEquals(DV.FALSE_DV, addAllAnalysis.getProperty(Property.IDENTITY));
        assertEquals(DV.FALSE_DV, addAllAnalysis.getProperty(Property.MODIFIED_METHOD));

        // PARAMETER 1

        ParameterAnalysis addAll0 = addAll.parameterAnalysis(0);

        assertEquals(DV.TRUE_DV, addAll0.getProperty(Property.IDENTITY));
        assertEquals(MultiLevel.NOT_CONTAINER_DV, addAll0.getProperty(Property.CONTAINER));
        assertEquals(MultiLevel.NOT_IGNORE_MODS_DV, addAll0.getProperty(Property.IGNORE_MODIFICATIONS));
        // method is non-modifying, so parameter is independent
        assertEquals(MultiLevel.INDEPENDENT_DV, addAll0.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE_DV, addAll0.getProperty(Property.IMMUTABLE));

        assertThrows(PropertyException.class, () -> addAll0.getProperty(Property.MODIFIED_METHOD));
        assertThrows(PropertyException.class, () -> addAll0.getProperty(Property.EXTENSION_CLASS));
        assertThrows(PropertyException.class, () -> addAll0.getProperty(Property.SINGLETON));
        assertThrows(PropertyException.class, () -> addAll0.getProperty(Property.FLUENT));
        assertThrows(PropertyException.class, () -> addAll0.getProperty(Property.FINALIZER));
        assertThrows(PropertyException.class, () -> addAll0.getProperty(Property.FINAL));
    }

    @Test
    public void testListGet() {
        TypeInfo list = typeContext.getFullyQualified(List.class);
        MethodInfo get = list.findUniqueMethod("get", 1);
        MethodAnalysis getAnalysis = get.methodAnalysis.get();

        // not @Container because of unbound parameter type
        assertEquals(MultiLevel.NOT_CONTAINER_DV, getAnalysis.getProperty(Property.CONTAINER));
        assertEquals(DV.FALSE_DV, getAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, getAnalysis.getProperty(Property.IMMUTABLE));

        // an unbound type parameter cannot be DEPENDENT
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, getAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.NULLABLE_DV, getAnalysis.getProperty(Property.NOT_NULL_EXPRESSION));
    }

    @Test
    public void testListAdd() {
        TypeInfo list = typeContext.getFullyQualified(List.class);
        TypeAnalysis typeAnalysis = list.typeAnalysis.get();
        assertEquals(MultiLevel.NOT_CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));

        MethodInfo add = list.findUniqueMethod("add", 1);
        MethodAnalysis addAnalysis = add.methodAnalysis.get();

        assertEquals(MultiLevel.CONTAINER_DV, addAnalysis.getProperty(Property.CONTAINER));
        assertEquals(DV.FALSE_DV, addAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_DV, addAnalysis.getProperty(Property.IMMUTABLE));

        ParameterAnalysis paramAnalysis = add.parameterAnalysis(0);

        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, paramAnalysis.getProperty(Property.IMMUTABLE), "In " + add.fullyQualifiedName);
        assertEquals(MultiLevel.NULLABLE_DV, paramAnalysis.getProperty(Property.NOT_NULL_PARAMETER));
        // not a container!
        assertEquals(DV.TRUE_DV, paramAnalysis.getProperty(Property.MODIFIED_VARIABLE));

        // independent, because the method is @NotModified and returns a boolean
        assertEquals(MultiLevel.INDEPENDENT_DV, paramAnalysis.getProperty(Property.INDEPENDENT));
    }

    @Test
    public void testArrayListAddIndex() {
        TypeInfo list = typeContext.getFullyQualified(ArrayList.class);
        MethodInfo add = list.findUniqueMethod("add", 2);
        MethodAnalysis addAnalysis = add.methodAnalysis.get();

        assertEquals(DV.FALSE_DV, addAnalysis.getProperty(Property.MODIFIED_METHOD));

        ParameterAnalysis paramAnalysis = add.parameterAnalysis(1);

        // independent, because the method is @NotModified
        assertEquals(MultiLevel.INDEPENDENT_DV, paramAnalysis.getProperty(Property.INDEPENDENT));
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

        assertEquals(DV.TRUE_DV, p0.getProperty(Property.IDENTITY));
        assertEquals(MultiLevel.CONTAINER_DV, p0.getProperty(Property.CONTAINER));
        assertEquals(MultiLevel.NULLABLE_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, p0.getProperty(Property.IMMUTABLE));
        // parameters of abstract methods are @Modified by default (unless primitive, E2)
        assertEquals(DV.TRUE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
        assertEquals(MultiLevel.INDEPENDENT_DV, p0.getProperty(Property.INDEPENDENT));

        MethodAnalysis toArrayAnalysis = toArray.methodAnalysis.get();
        assertEquals(MultiLevel.CONTAINER_DV, toArrayAnalysis.getProperty(Property.CONTAINER));
        assertEquals(DV.FALSE_DV, toArrayAnalysis.getProperty(Property.MODIFIED_METHOD));

        assertEquals(MultiLevel.NULLABLE_DV, toArrayAnalysis.getProperty(Property.NOT_NULL_EXPRESSION));
        assertEquals(MultiLevel.EFFECTIVELY_FINAL_FIELDS_DV, toArrayAnalysis.getProperty(Property.IMMUTABLE));
        // AAPI will be @Independent(hc=true), but without, the default is dependent (the list could be backed by this array)
        assertEquals(MultiLevel.DEPENDENT_DV, toArrayAnalysis.getProperty(Property.INDEPENDENT));
    }

    @Test
    public void testConstructor() {

        // CONSTRUCTOR 1

        TypeInfo arrayList = typeContext.getFullyQualified(ArrayList.class);

        MethodInfo emptyConstructor = arrayList.findConstructor(0);
        MethodAnalysis emptyAnalysis = emptyConstructor.methodAnalysis.get();
        assertEquals(MultiLevel.INDEPENDENT_DV, emptyAnalysis.getProperty(Property.INDEPENDENT));

        // all constructors are modified, by definition
        assertEquals(DV.TRUE_DV, emptyAnalysis.getProperty(Property.MODIFIED_METHOD));

        TypeInfo hashMap = typeContext.getFullyQualified(HashMap.class);

        // CONSTRUCTOR 2

        // constructor with int and float parameters
        MethodInfo twoConstructor = hashMap.findConstructor(2);
        MethodAnalysis twoAnalysis = twoConstructor.methodAnalysis.get();
        assertEquals(MultiLevel.INDEPENDENT_DV, twoAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(DV.TRUE_DV, twoAnalysis.getProperty(Property.MODIFIED_METHOD));

        ParameterAnalysis intAnalysis = twoConstructor.parameterAnalysis(0);

        assertEquals(MultiLevel.EFFECTIVELY_NOT_NULL_DV, intAnalysis.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_DV, intAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.CONTAINER_DV, intAnalysis.getProperty(Property.CONTAINER));
        assertEquals(DV.FALSE_DV, intAnalysis.getProperty(Property.MODIFIED_VARIABLE));
        assertThrows(PropertyException.class, () -> intAnalysis.getProperty(Property.MODIFIED_METHOD));
    }

    @Test
    public void testField() {
        TypeInfo system = typeContext.getFullyQualified(System.class);
        FieldInfo out = system.getFieldByName("out", true);
        FieldAnalysis outAnalysis = out.fieldAnalysis.get();

        assertEquals(MultiLevel.NOT_CONTAINER_DV, outAnalysis.getProperty(Property.CONTAINER));
        assertEquals(MultiLevel.MUTABLE_DV, outAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(DV.TRUE_DV, outAnalysis.getProperty(Property.FINAL));
        assertEquals(DV.FALSE_DV, outAnalysis.getProperty(Property.MODIFIED_OUTSIDE_METHOD));
        assertEquals(MultiLevel.DEPENDENT_DV, outAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.NULLABLE_DV, outAnalysis.getProperty(Property.EXTERNAL_NOT_NULL));

        if (outAnalysis instanceof FieldAnalysisImpl outAnalysisImpl) {
            assertTrue(outAnalysisImpl.properties.containsKey(Property.FINAL));
            assertTrue(outAnalysisImpl.properties.containsKey(Property.EXTERNAL_CONTAINER));
        } else fail();

        Expression value = outAnalysis.getValue();
        assertTrue(value.isDone());
        assertEquals("nullable instance type PrintStream", value.toString());
    }

    @Test
    public void testIntField() {
        TypeInfo integer = typeContext.getFullyQualified(Integer.class);
        FieldInfo bytes = integer.getFieldByName("BYTES", true);
        FieldAnalysis bytesAnalysis = bytes.fieldAnalysis.get();

        assertEquals(MultiLevel.CONTAINER_DV, bytesAnalysis.getProperty(Property.EXTERNAL_CONTAINER));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_DV, bytesAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(DV.TRUE_DV, bytesAnalysis.getProperty(Property.FINAL));
        assertEquals(DV.FALSE_DV, bytesAnalysis.getProperty(Property.MODIFIED_OUTSIDE_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_DV, bytesAnalysis.getProperty(Property.INDEPENDENT));

        if (bytesAnalysis instanceof FieldAnalysisImpl bytesAnalysisImpl) {
            assertTrue(bytesAnalysisImpl.properties.containsKey(Property.EXTERNAL_CONTAINER));
            assertTrue(bytesAnalysisImpl.properties.containsKey(Property.FINAL));
        } else fail();

    }

    /**
     * By default, {@link Throwable} is @Dependent
     */
    @Test
    public void testThrowable() {
        TypeInfo throwable = typeContext.getFullyQualified(Throwable.class);
        TypeAnalysis typeAnalysis = throwable.typeAnalysis.get();
        assertEquals(MultiLevel.NOT_CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));
        assertEquals(MultiLevel.MUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.DEPENDENT_DV, typeAnalysis.getProperty(Property.INDEPENDENT));

        MethodInfo equals = throwable.findUniqueMethod("getStackTrace", 0);
        MethodAnalysis methodAnalysis = equals.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.DEPENDENT_DV, methodAnalysis.getProperty(Property.INDEPENDENT));
    }


    /**
     * {@link IllegalFormatException} has no methods or constructors of its own, but it does share those of
     * {@link Throwable} and {@link Object}. As a consequence, it is dependent
     */
    @Test
    public void testIllegalFormatException() {
        TypeInfo illegalFormatException = typeContext.getFullyQualified(IllegalFormatException.class);
        TypeAnalysis typeAnalysis = illegalFormatException.typeAnalysis.get();
        assertEquals(MultiLevel.NOT_CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));
        assertEquals(MultiLevel.MUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.DEPENDENT_DV, typeAnalysis.getProperty(Property.INDEPENDENT));
    }


    @Test
    public void testMapPut() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Map.class);
        TypeAnalysis typeAnalysis = typeInfo.typeAnalysis.get();
        assertEquals(MultiLevel.NOT_CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));

        MethodInfo methodInfo = typeInfo.findUniqueMethod("put", 2);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(MultiLevel.INDEPENDENT_HC_DV, methodAnalysis.getProperty(Property.INDEPENDENT));

        // key
        ParameterAnalysis p0 = methodInfo.parameterAnalysis(0);
        assertEquals(MultiLevel.NULLABLE_DV, p0.getProperty(Property.NOT_NULL_PARAMETER));
        assertEquals(DV.TRUE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
        assertEquals(MultiLevel.INDEPENDENT_DV, p0.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_IMMUTABLE_HC_DV, p0.getProperty(Property.IMMUTABLE));
    }


    @Test
    public void testRandomNextInt() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Random.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("nextInt", 0);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
    }


    @Test
    public void testObjectsRequireNonNull() {
        TypeInfo typeInfo = typeContext.getFullyQualified(Objects.class);
        MethodInfo methodInfo = typeInfo.findUniqueMethod("requireNonNull", 2);
        MethodAnalysis methodAnalysis = methodInfo.methodAnalysis.get();
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.MODIFIED_METHOD));
        assertEquals(DV.FALSE_DV, methodAnalysis.getProperty(Property.IDENTITY));
        ParameterAnalysis p0 = methodAnalysis.getParameterAnalyses().get(0);
        assertEquals(DV.TRUE_DV, p0.getProperty(Property.MODIFIED_VARIABLE));
    }

    // see: essential, hardcoded in AnnotationAPIAnalyser
    @Test
    public void testBoxedContainer() {
        TypeInfo integer = typeContext.getFullyQualified(Integer.class);
        assertEquals(MultiLevel.CONTAINER_DV, integer.typeAnalysis.get().getProperty(Property.CONTAINER));
        TypeInfo boxedBool = typeContext.getFullyQualified(Boolean.class);
        assertEquals(MultiLevel.CONTAINER_DV, boxedBool.typeAnalysis.get().getProperty(Property.CONTAINER));
    }
}
