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
import org.e2immu.analyser.analysis.TypeAnalysis;
import org.e2immu.analyser.config.AnnotatedAPIConfiguration;
import org.e2immu.analyser.config.Configuration;
import org.e2immu.analyser.config.InputConfiguration;
import org.e2immu.analyser.inspector.TypeContext;
import org.e2immu.analyser.model.MultiLevel;
import org.e2immu.analyser.parser.Input;
import org.e2immu.analyser.parser.Message;
import org.e2immu.analyser.parser.Parser;
import org.junit.jupiter.api.BeforeAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

import static org.e2immu.analyser.parser.CommonTestRunner.DEFAULT_ANNOTATED_API_DIRS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public abstract class CommonAnnotatedAPI {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommonAnnotatedAPI.class);

    protected static TypeContext typeContext;
    protected static List<Message> errors;

    @BeforeAll
    public static void beforeClass() throws IOException {
        InputConfiguration.Builder inputConfigurationBuilder = new InputConfiguration.Builder()
                .setAlternativeJREDirectory("/Library/Java/JavaVirtualMachines/adoptopenjdk-16.jdk/Contents/Home")
                .addClassPath("jmods/java.base.jmod")
                .addClassPath("jmods/java.xml.jmod")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/slf4j")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "org/junit/jupiter/api")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "ch/qos/logback/core/spi")
                .addClassPath(Input.JAR_WITH_PATH_PREFIX + "com/github/javaparser/ast");
        AnnotatedAPIConfiguration.Builder annotatedAPIConfiguration = new AnnotatedAPIConfiguration.Builder()
                .addAnnotatedAPISourceDirs(DEFAULT_ANNOTATED_API_DIRS);
        Configuration configuration = new Configuration.Builder()
                .setInputConfiguration(inputConfigurationBuilder.build())
                .setAnnotatedAPIConfiguration(annotatedAPIConfiguration.build())
                .addDebugLogTargets("analyser")
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
        // not stopping here if there's errors, TestAnnotatedAPIErrors will fail
    }

    protected void testImmutableContainerType(TypeAnalysis typeAnalysis, boolean hcImmutable, boolean hcIndependent) {
        DV immutableDv = hcImmutable ? MultiLevel.EFFECTIVELY_E2IMMUTABLE_DV : MultiLevel.EFFECTIVELY_RECURSIVELY_IMMUTABLE_DV;
        assertEquals(immutableDv, typeAnalysis.getProperty(Property.IMMUTABLE));
        assertEquals(MultiLevel.CONTAINER_DV, typeAnalysis.getProperty(Property.CONTAINER));
        DV independentDv = hcIndependent ? MultiLevel.INDEPENDENT_1_DV : MultiLevel.INDEPENDENT_DV;
        assertEquals(independentDv, typeAnalysis.getProperty(Property.INDEPENDENT));

        assertEquals(DV.FALSE_DV, typeAnalysis.getProperty(Property.EXTENSION_CLASS));
        assertEquals(DV.FALSE_DV, typeAnalysis.getProperty(Property.UTILITY_CLASS));
        assertEquals(DV.FALSE_DV, typeAnalysis.getProperty(Property.SINGLETON));
        assertEquals(DV.FALSE_DV, typeAnalysis.getProperty(Property.FINALIZER));

        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(Property.FLUENT));
        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(Property.IDENTITY));
        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(Property.NOT_NULL_EXPRESSION));
        assertThrows(PropertyException.class, () -> typeAnalysis.getProperty(Property.MODIFIED_METHOD));
    }
}
