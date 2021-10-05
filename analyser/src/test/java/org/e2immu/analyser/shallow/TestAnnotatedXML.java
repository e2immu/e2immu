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
public class TestAnnotatedXML {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestAnnotatedXML.class);

    private static TypeContext typeContext;

    @BeforeAll
    public static void beforeClass() throws IOException {
        InputConfiguration.Builder inputConfigurationBuilder = new InputConfiguration.Builder()
                .addClassPath(InputConfiguration.CLASSPATH_WITHOUT_ANNOTATED_APIS);
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

    // in annotated XML, not hardcoded
    @Test
    public void testFloat() {
        TypeInfo optional = typeContext.getFullyQualified(Float.class);
        TypeAnalysis typeAnalysis = optional.typeAnalysis.get();
        assertEquals(MultiLevel.INDEPENDENT, typeAnalysis.getProperty(VariableProperty.INDEPENDENT));
        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE, typeAnalysis.getProperty(VariableProperty.IMMUTABLE));
    }

}
