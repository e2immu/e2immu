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
import org.e2immu.analyser.parser.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collection;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.junit.jupiter.api.Assertions.*;

/*
Tests the default annotations of byte-code inspected+analysed types, without annotated APIs on top.

All property maps are empty at this stage!

 */
public class TestDefaultAnnotations {

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
        parser.run();
        typeContext = parser.getTypeContext();

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
            assertTrue(sizeAnalysisImpl.properties.isEmpty());
        } else fail();

        // METHOD 2

        MethodInfo addAll = collection.findUniqueMethod("addAll", 1);
        MethodAnalysis addAllAnalysis = addAll.methodAnalysis.get();
        assertEquals(Level.FALSE, addAllAnalysis.getProperty(VariableProperty.FLUENT));
        assertEquals(Level.FALSE, addAllAnalysis.getProperty(VariableProperty.IDENTITY));
        assertEquals(Level.FALSE, addAllAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));

        if (addAllAnalysis instanceof MethodAnalysisImpl addAllAnalysisImpl) {
            assertTrue(addAllAnalysisImpl.properties.isEmpty());
        } else fail();

        // PARAMETER 1

        ParameterAnalysis addAll0 = addAll.methodInspection.get().getParameters().get(0).parameterAnalysis.get();
        if (addAll0 instanceof ParameterAnalysisImpl addAll0Impl) {
            assertTrue(addAll0Impl.properties.isEmpty());
        } else fail();

        assertEquals(Level.TRUE, addAll0.getProperty(VariableProperty.IDENTITY));
        assertEquals(Level.FALSE, addAll0.getProperty(VariableProperty.CONTAINER));
        assertEquals(Level.FALSE, addAll0.getProperty(VariableProperty.IGNORE_MODIFICATIONS));
        assertEquals(MultiLevel.DEPENDENT, addAll0.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(MultiLevel.MUTABLE, addAll0.getProperty(VariableProperty.IMMUTABLE));

        assertThrows(PropertyException.class, () -> addAll0.getProperty(VariableProperty.MODIFIED_METHOD));
        assertThrows(PropertyException.class, () -> addAll0.getProperty(VariableProperty.EXTENSION_CLASS));
        assertThrows(PropertyException.class, () -> addAll0.getProperty(VariableProperty.SINGLETON));
        assertThrows(PropertyException.class, () -> addAll0.getProperty(VariableProperty.FLUENT));
        assertThrows(PropertyException.class, () -> addAll0.getProperty(VariableProperty.FINALIZER));
        assertThrows(PropertyException.class, () -> addAll0.getProperty(VariableProperty.FINAL));

        // FIELD

        TypeInfo system = typeContext.getFullyQualified(System.class);
        assertNotNull(system);

        FieldInfo out = system.getFieldByName("out", true);
        FieldAnalysis outAnalysis = out.fieldAnalysis.get();

        assertEquals(Level.FALSE, outAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(MultiLevel.MUTABLE, outAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(Level.TRUE, outAnalysis.getProperty(VariableProperty.FINAL));

        if (outAnalysis instanceof FieldAnalysisImpl outAnalysisImpl) {
            assertEquals(1, outAnalysisImpl.properties.size()); // FINAL
            assertTrue(outAnalysisImpl.properties.containsKey(VariableProperty.FINAL));
        } else fail();
    }
}
