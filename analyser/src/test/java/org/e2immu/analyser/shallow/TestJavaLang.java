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
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.*;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Parser;
import org.e2immu.analyser.parser.Primitives;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Serializable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.parser.CommonTestRunner.DEFAULT_ANNOTATED_API_DIRS;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestJavaLang {


    private static TypeContext typeContext;

    @BeforeAll
    public static void beforeClass() throws IOException {
        InputConfiguration.Builder inputConfigurationBuilder = new InputConfiguration.Builder()
                .addClassPath("jmods/java.base.jmod")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit/jupiter/api")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi");
        AnnotatedAPIConfiguration.Builder annotatedAPIConfiguration = new AnnotatedAPIConfiguration.Builder()
                .addAnnotatedAPISourceDirs(DEFAULT_ANNOTATED_API_DIRS);
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(inputConfigurationBuilder.build())
                .setAnnotatedAPIConfiguration(annotatedAPIConfiguration.build())
                .addDebugLogTargets(Stream.of(DELAYED).map(Enum::toString).collect(Collectors.joining(",")))
                .build();
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        parser.run();
        typeContext = parser.getTypeContext();

    }

    private void testE2ContainerType(TypeAnalysis typeAnalysis) {
        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE, typeAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(Level.TRUE, typeAnalysis.getProperty(VariableProperty.CONTAINER));

        assertEquals(Level.FALSE, typeAnalysis.getProperty(VariableProperty.EXTENSION_CLASS));
        assertEquals(Level.FALSE, typeAnalysis.getProperty(VariableProperty.UTILITY_CLASS));
        assertEquals(Level.FALSE, typeAnalysis.getProperty(VariableProperty.SINGLETON));
        assertEquals(Level.FALSE, typeAnalysis.getProperty(VariableProperty.FINALIZER));

        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(VariableProperty.FLUENT));
        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(VariableProperty.IDENTITY));
        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
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
        assertEquals(MultiLevel.INDEPENDENT_2, methodAnalysis.getProperty(VariableProperty.INDEPENDENT));
    }

    @Test
    public void testStringBuilder() {
        TypeInfo sb = typeContext.getFullyQualified(StringBuilder.class);
        TypeAnalysis typeAnalysis = sb.typeAnalysis.get();

        MethodInfo appendBoolean = sb.typeInspection.get().methodStream(TypeInspection.Methods.THIS_TYPE_ONLY)
                .filter(m -> "append".equals(m.name))
                .filter(m -> m.methodInspection.get().getParameters().size() == 1 &&
                        Primitives.isBoolean(m.methodInspection.get().getParameters().get(0).parameterizedType))
                .findFirst().orElseThrow();
        MethodAnalysis appendAnalysis = appendBoolean.methodAnalysis.get();

        // a @Fluent method in a modifiable type is @Dependent (it returns 'this', which is modifiable)
        assertEquals(Level.TRUE, appendAnalysis.getProperty(VariableProperty.FLUENT));
        assertEquals(MultiLevel.DEPENDENT, appendAnalysis.getProperty(VariableProperty.INDEPENDENT));

        // as a consequence, the type is DEPENDENT as well
        assertEquals(MultiLevel.DEPENDENT, typeAnalysis.getProperty(VariableProperty.INDEPENDENT));

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
