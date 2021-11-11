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
import org.e2immu.analyser.model.Level;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.model.TypeAnalysis;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.e2immu.analyser.parser.CommonTestRunner.DEFAULT_ANNOTATED_API_DIRS;
import static org.e2immu.analyser.util.Logger.LogTarget.ANALYSER;
import static org.e2immu.analyser.util.Logger.LogTarget.DELAYED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public abstract class CommonAnnotatedAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonAnnotatedAPI.class);

    protected static TypeContext typeContext;
    protected static List<Message> errors;

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
                .addDebugLogTargets(Stream.of(DELAYED, ANALYSER).map(Enum::toString).collect(Collectors.joining(",")))
                .build();
        configuration.initializeLoggers();
        Parser parser = new Parser(configuration);
        parser.run();
        typeContext = parser.getTypeContext();
        errors = parser.getMessages()
                .filter(m -> m.message().severity == Message.Severity.ERROR)
                .toList();
        LOGGER.info("Have {} error messages", errors.size());
        errors.forEach(e -> LOGGER.info("Error: " + e));
        // we do expect some
        long ownErrors = errors.stream()
                .filter(m -> m.location().info.getTypeInfo().fullyQualifiedName.startsWith("org.e2immu"))
                .peek(m -> LOGGER.info("OWN ERROR: {}", m))
                .count();
        assertEquals(0L, ownErrors);
    }

    protected void testERContainerType(TypeAnalysis typeAnalysis) {
        assertEquals(MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV, typeAnalysis.getProperty(VariableProperty.IMMUTABLE));
        assertEquals(Level.TRUE_DV, typeAnalysis.getProperty(VariableProperty.CONTAINER));
        assertEquals(MultiLevel.INDEPENDENT_1_DV, typeAnalysis.getProperty(VariableProperty.INDEPENDENT));

        assertEquals(Level.FALSE_DV, typeAnalysis.getProperty(VariableProperty.EXTENSION_CLASS));
        assertEquals(Level.FALSE_DV, typeAnalysis.getProperty(VariableProperty.UTILITY_CLASS));
        assertEquals(Level.FALSE_DV, typeAnalysis.getProperty(VariableProperty.SINGLETON));
        assertEquals(Level.FALSE_DV, typeAnalysis.getProperty(VariableProperty.FINALIZER));

        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(VariableProperty.FLUENT));
        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(VariableProperty.IDENTITY));
        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(VariableProperty.NOT_NULL_EXPRESSION));
        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(VariableProperty.MODIFIED_METHOD));
    }
}
