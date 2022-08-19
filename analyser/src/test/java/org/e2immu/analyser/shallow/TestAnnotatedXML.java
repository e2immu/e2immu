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
import org.e2immu.analyser.analysis.MethodAnalysis;
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.MethodInfo;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeInfo;
import org.e2immu.analyser.model.impl.LocationImpl;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/*
Tests the default annotations of byte-code inspected+analysed types, without annotated APIs on top.

All property maps are empty at this stage!

 */
public class TestAnnotatedXML {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestAnnotatedXML.class);

    private static TypeContext typeContext;

    @BeforeAll
    public static void beforeClass() throws IOException {
        InputConfiguration.Builder inputConfigurationBuilder = new InputConfiguration.Builder()
                .addClassPath(InputConfiguration.CLASSPATH_WITHOUT_ANNOTATED_APIS);
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
    public void testString() {
        TypeInfo object = typeContext.getFullyQualified(String.class);
        TypeAnalysis typeAnalysis = object.typeAnalysis.get();
        assertEquals(MultiLevel.CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));
        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT_DV, typeAnalysis.getProperty(Property.INDEPENDENT));
    }

    // hardcoded
    @Test
    public void testObject() {
        TypeInfo object = typeContext.getFullyQualified(Object.class);
        TypeAnalysis typeAnalysis = object.typeAnalysis.get();
        assertEquals(MultiLevel.CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));
        assertEquals(MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.INDEPENDENT_1_DV, typeAnalysis.getProperty(Property.INDEPENDENT));
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

    // in annotated XML, not hardcoded
    @Test
    public void testFloat() {
        TypeInfo optional = typeContext.getFullyQualified(Float.class);
        TypeAnalysis typeAnalysis = optional.typeAnalysis.get();
        assertEquals(MultiLevel.INDEPENDENT_DV, typeAnalysis.getProperty(Property.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, typeAnalysis.getProperty(Property.IMMUTABLE));
    }

}
